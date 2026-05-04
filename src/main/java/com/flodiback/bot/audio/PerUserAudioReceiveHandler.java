package com.flodiback.bot.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.bot.stt.BotSttListener;
import com.flodiback.domain.speech.stt.SttProvider;
import com.orctom.vad4j.VAD;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.OpusPacket;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * 길드 단위 음성 수신 핸들러.
 *
 * <p>역할:
 * - 화자별 오디오 수신 통계 수집
 * - 화자별 STT 세션 start/send/end 관리
 * - 무음 구간 기준으로 발화 세션 종료(commit)
 */
public class PerUserAudioReceiveHandler implements AudioReceiveHandler {
    private static final Logger log = LoggerFactory.getLogger(PerUserAudioReceiveHandler.class);

    // 로그 과다 방지를 위한 주기
    private static final long LOG_INTERVAL_PACKETS = 50;
    // 이 시간(ms) 동안 화자 오디오가 없으면 해당 STT 세션을 종료한다.
    private static final long STT_SILENCE_END_MS = 1800L;
    // 무음 종료 감시 주기(ms)
    private static final long STT_SILENCE_WATCH_INTERVAL_MS = 250L;
    private static final int VAD_START_RMS_THRESHOLD = 600;
    private static final int VAD_END_RMS_THRESHOLD = 500;
    private static final float VAD_START_PROB_THRESHOLD = 0.62f;
    private static final float VAD_END_PROB_THRESHOLD = 0.45f;
    private static final int VAD_MIN_CONSECUTIVE_SPEECH_FRAMES = 3;
    private static final int VAD_PREROLL_MS = 400;
    private static final long VAD_CALIBRATION_WINDOW_MS = 180_000L;

    private final long guildId;
    private final long meetingId;
    private final SttProvider sttProvider;
    private final ScheduledExecutorService silenceWatcher;
    private final Guild guild;
    private volatile MessageChannel captionChannel;

    // 사용자 ID별 누적 통계
    private final Map<Long, SpeakerStats> statsByUserId = new ConcurrentHashMap<>();
    private final Map<Long, VAD> webRtcVadByUserId = new ConcurrentHashMap<>();
    // 사용자 ID별 활성 STT 세션
    private final Map<Long, ActiveSttSession> activeSttSessionsByUserId = new ConcurrentHashMap<>();

    // 인코딩/디코딩 계층 통계
    private final AtomicLong encodedPacketCount = new AtomicLong();
    private final AtomicLong userPacketCount = new AtomicLong();
    private final AtomicLong decodableEncodedPacketCount = new AtomicLong();
    private final AtomicLong nonDecodableEncodedPackets = new AtomicLong();
    private final AtomicLong decodedFromEncodedPacketCount = new AtomicLong();
    private final AtomicLong decodedFromEncodedByteCount = new AtomicLong();
    // 디코딩 null/실패 프레임 카운트
    private final AtomicLong decodeFailureCount = new AtomicLong();

    // 첫 디코딩 실패 사유를 기록해 원인 파악에 사용
    private final AtomicReference<String> firstDecodeFailureReason = new AtomicReference<>();

    // 첫 수신 순간 강조 로그 플래그
    private final AtomicBoolean firstEncodedLogged = new AtomicBoolean(false);
    private final AtomicBoolean firstUserLogged = new AtomicBoolean(false);
    private final AtomicBoolean webRtcVadUnavailableLogged = new AtomicBoolean(false);
    private final AtomicLong vadStartThreshold = new AtomicLong(VAD_START_RMS_THRESHOLD);
    private final AtomicLong vadEndThreshold = new AtomicLong(VAD_END_RMS_THRESHOLD);
    private final AtomicLong calibrationStartedAtMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean calibrationDone = new AtomicBoolean(false);
    private final List<Integer> calibrationNoiseRms = new ArrayList<>();
    private final List<Integer> calibrationSpeechRms = new ArrayList<>();

    /**
     * 하위 호환 생성자.
     *
     * @deprecated Guild 기반 화자명 조회를 쓰려면
     *     {@link #PerUserAudioReceiveHandler(long, Guild, SttProvider, long)} 생성자를 사용하세요.
     */
    @Deprecated(forRemoval = false)
    public PerUserAudioReceiveHandler(long guildId, SttProvider sttProvider, long meetingId) {
        this(guildId, null, sttProvider, meetingId);
    }

    public PerUserAudioReceiveHandler(long guildId, Guild guild, SttProvider sttProvider, long meetingId) {
        this.guildId = guildId;
        this.guild = guild;
        this.sttProvider = Objects.requireNonNull(sttProvider);
        this.meetingId = meetingId;
        this.silenceWatcher = Executors.newSingleThreadScheduledExecutor(new SilenceWatcherThreadFactory(guildId));
        this.silenceWatcher.scheduleAtFixedRate(
                this::endInactiveSttSessionsOnSchedule,
                STT_SILENCE_WATCH_INTERVAL_MS,
                STT_SILENCE_WATCH_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    public void updateCaptionChannel(MessageChannel captionChannel) {
        this.captionChannel = captionChannel;
    }

    @Override
    public boolean canReceiveUser() {
        // UserAudio 콜백 수신 허용(이름/사용자 단위 통계 갱신용)
        return true;
    }

    @Override
    public boolean canReceiveEncoded() {
        // Opus 패킷 수신 허용(실제 STT 전송 PCM은 여기서 디코딩한 데이터를 사용)
        return true;
    }

    /**
     * Opus 패킷 수신 콜백.
     *
     * <p>핵심 처리:
     * 1) 통계 누적
     * 2) 디코딩 가능하면 PCM 추출
     * 3) 화자별 STT 세션 start/send
     * 4) 무음 세션 end
     */
    @Override
    public void handleEncodedAudio(OpusPacket packet) {
        long now = System.currentTimeMillis();
        long encodedCount = encodedPacketCount.incrementAndGet();
        long userId = packet.getUserId();

        // 화자별 통계를 미리 준비한다. UserAudio가 아직 안 오면 user-<id> placeholder를 사용한다.
        SpeakerStats stats = statsByUserId.computeIfAbsent(userId, ignored -> new SpeakerStats("user-" + userId));
        long userEncodedPackets = stats.encodedPacketCount.incrementAndGet();
        stats.encodedByteCount.addAndGet(packet.getOpusAudio().length);
        stats.lastSeenAtMs.set(now);

        boolean canDecode = packet.canDecode();
        if (!canDecode) {
            nonDecodableEncodedPackets.incrementAndGet();
            if (firstDecodeFailureReason.get() == null) {
                try {
                    packet.decode();
                } catch (IllegalStateException ex) {
                    firstDecodeFailureReason.compareAndSet(null, ex.getMessage());
                }
            }
        } else {
            decodableEncodedPacketCount.incrementAndGet();
            try {
                byte[] decodedPcm = decodePcmSafely(packet, userId);
                if (decodedPcm == null || decodedPcm.length == 0) {
                    // 일부 프레임은 canDecode=true여도 실제 PCM 추출이 실패할 수 있다.
                    // (예: DAVE decrypt 실패, 순서 꼬임 등)
                    // 이 프레임은 버리고 다음 프레임으로 진행한다.
                    return;
                }
                decodedFromEncodedPacketCount.incrementAndGet();
                decodedFromEncodedByteCount.addAndGet(decodedPcm.length);
                stats.decodedPcmPacketCount.incrementAndGet();
                stats.decodedPcmByteCount.addAndGet(decodedPcm.length);

                int rms = calculateRms(decodedPcm);
                maybeCalibrateVad(now, rms);
                SpeechEvidence speechEvidence = detectSpeechEvidence(userId, decodedPcm, rms);

                ForwardDecision decision = evaluateVad(userId, stats, decodedPcm, speechEvidence, now);
                if (decision == ForwardDecision.DROP) {
                    // no-op
                } else if (decision == ForwardDecision.START_WITH_PREROLL) {
                    ActiveSttSession session = getOrCreateSttSession(userId, stats.userName, now);
                    if (session == null) {
                        // no-op
                    } else {
                        session.lastAudioAtMs.set(now);
                        for (byte[] preRollFrame : stats.preRollBuffer.drain()) {
                            sendToActiveSession(session, userId, preRollFrame, now);
                        }
                    }
                } else {
                    forwardPcmToStt(userId, stats.userName, decodedPcm, now);
                }
            } catch (Exception decodeException) {
                nonDecodableEncodedPackets.incrementAndGet();
                firstDecodeFailureReason.compareAndSet(null, decodeException.getMessage());
                long failures = decodeFailureCount.incrementAndGet();
                // 실시간 수신 루프에서 매 프레임 warn을 찍으면 로그가 폭증하므로 샘플링한다.
                if (failures <= 3 || failures % 200 == 0) {
                    log.warn(
                            "[음성/디코드실패] guildId={}, userId={}, decodeFailures={}, firstReason={}",
                            guildId,
                            userId,
                            failures,
                            firstDecodeFailureReason.get() == null ? "-" : firstDecodeFailureReason.get(),
                            decodeException);
                }
            }
        }

        if (firstEncodedLogged.compareAndSet(false, true)) {
            log.info("[음성/인코드첫수신] guildId={}, firstUserId={}, encodedPackets={}", guildId, userId, encodedCount);
        }
        if (encodedCount == 1 || encodedCount % LOG_INTERVAL_PACKETS == 0) {
            log.info(
                    "[음성/인코드수신] guildId={}, encodedPackets={}, userId={}, userEncodedPackets={}, canDecode={}, decodeFailures={}, userPackets={}, activeSttSessions={}",
                    guildId,
                    encodedCount,
                    userId,
                    userEncodedPackets,
                    canDecode,
                    decodeFailureCount.get(),
                    userPacketCount.get(),
                    activeSttSessionsByUserId.size());
        }
    }

    /**
     * UserAudio 콜백.
     *
     * <p>현재 STT 송신은 encoded 경로를 사용하지만,
     * 이 콜백은 사용자명 보정 및 user 레벨 통계에 계속 활용한다.
     */
    @Override
    public void handleUserAudio(UserAudio userAudio) {
        long userId = userAudio.getUser().getIdLong();
        String userName = userAudio.getUser().getName();
        byte[] pcm = userAudio.getAudioData(1.0);
        int frameBytes = pcm.length;

        if (firstUserLogged.compareAndSet(false, true)) {
            log.info(
                    "[음성/유저PCM첫수신] guildId={}, userId={}, userName={}, frameBytes={}",
                    guildId,
                    userId,
                    userName,
                    frameBytes);
        }

        SpeakerStats stats = statsByUserId.computeIfAbsent(userId, ignored -> new SpeakerStats(userName));
        stats.updateUserNameIfPlaceholder(userName);

        long packetCount = stats.packetCount.incrementAndGet();
        long totalUserPackets = userPacketCount.incrementAndGet();
        stats.byteCount.addAndGet(frameBytes);
        stats.lastSeenAtMs.set(System.currentTimeMillis());

        if (packetCount == 1 || packetCount % LOG_INTERVAL_PACKETS == 0) {
            log.info(
                    "[음성/유저PCM수신] guildId={}, userId={}, userName={}, userPackets={}, bytes={}, totalUserPackets={}, encodedPackets={}, decodeFailures={}",
                    guildId,
                    userId,
                    userName,
                    packetCount,
                    stats.byteCount.get(),
                    totalUserPackets,
                    encodedPacketCount.get(),
                    decodeFailureCount.get());
        }
    }

    /**
     * 길드 음성 세션을 명시적으로 종료할 때(예: !leave) 모든 STT 세션을 end(commit)한다.
     */
    public void closeAllSttSessions() {
        silenceWatcher.shutdownNow();
        for (Map.Entry<Long, ActiveSttSession> entry : activeSttSessionsByUserId.entrySet()) {
            Long userId = entry.getKey();
            ActiveSttSession activeSession = entry.getValue();
            if (activeSttSessionsByUserId.remove(userId, activeSession)) {
                safelyEndSession(activeSession, "manual_close_all");
            }
        }
        for (VAD vad : webRtcVadByUserId.values()) {
            closeVadQuietly(vad);
        }
        webRtcVadByUserId.clear();
    }

    /**
     * 디스코드 `!stats`에 출력할 수신/디코딩/STT 상태 요약 문자열.
     */
    public String getStatsSummary() {
        long encoded = encodedPacketCount.get();
        long user = userPacketCount.get();
        long decodable = decodableEncodedPacketCount.get();
        long nonDecodable = nonDecodableEncodedPackets.get();
        long decodedPcmPackets = decodedFromEncodedPacketCount.get();
        long decodedPcmBytes = decodedFromEncodedByteCount.get();
        String decodeFailureReason = firstDecodeFailureReason.get();

        if (statsByUserId.isEmpty()) {
            return "아직 수신된 화자 오디오가 없어. (encodedPackets="
                    + encoded
                    + ", userPackets="
                    + user
                    + ", decodableEncodedPackets="
                    + decodable
                    + ", nonDecodableEncodedPackets="
                    + nonDecodable
                    + ", decodedFromEncodedPackets="
                    + decodedPcmPackets
                    + ", decodedFromEncodedBytes="
                    + decodedPcmBytes
                    + ", activeSttSessions="
                    + activeSttSessionsByUserId.size()
                    + ", vadStartThreshold="
                    + vadStartThreshold.get()
                    + ", vadEndThreshold="
                    + vadEndThreshold.get()
                    + ", firstDecodeFailureReason="
                    + (decodeFailureReason == null ? "-" : decodeFailureReason)
                    + ")";
        }

        StringBuilder builder = new StringBuilder("화자별 수신 통계 (encodedPackets="
                + encoded
                + ", userPackets="
                + user
                + ", decodableEncodedPackets="
                + decodable
                + ", nonDecodableEncodedPackets="
                + nonDecodable
                + ", decodedFromEncodedPackets="
                + decodedPcmPackets
                + ", decodedFromEncodedBytes="
                + decodedPcmBytes
                + ", activeSttSessions="
                + activeSttSessionsByUserId.size()
                + ", firstDecodeFailureReason="
                + (decodeFailureReason == null ? "-" : decodeFailureReason)
                + ")");

        statsByUserId.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        entry -> -entry.getValue().encodedPacketCount.get()))
                .forEach(entry -> {
                    SpeakerStats stats = entry.getValue();
                    builder.append("\n- ")
                            .append(stats.userName)
                            .append(" (")
                            .append(entry.getKey())
                            .append("): encodedPackets=")
                            .append(stats.encodedPacketCount.get())
                            .append(", encodedBytes=")
                            .append(stats.encodedByteCount.get())
                            .append(", decodedPcmPackets=")
                            .append(stats.decodedPcmPacketCount.get())
                            .append(", decodedPcmBytes=")
                            .append(stats.decodedPcmByteCount.get())
                            .append(", userPackets=")
                            .append(stats.packetCount.get())
                            .append(", userBytes=")
                            .append(stats.byteCount.get())
                            .append(", vadState=")
                            .append(stats.vadState);
                });
        return builder.toString();
    }

    /**
     * PCM을 화자 세션으로 라우팅한다.
     * - 세션이 없으면 생성+start
     * - 있으면 마지막 활동 시각 갱신
     * - 그리고 sendPcm 호출
     */
    private void forwardPcmToStt(long userId, String speakerName, byte[] decodedPcm, long now) {
        ActiveSttSession session = getOrCreateSttSession(userId, speakerName, now);
        if (session == null) {
            return;
        }

        session.lastAudioAtMs.set(now);

        try {
            sendToActiveSession(session, userId, decodedPcm, now);
        } catch (Exception sendException) {
            log.warn(
                    "[STT/PCM전달실패] guildId={}, sessionId={}, userId={}",
                    guildId,
                    session.sessionId,
                    userId,
                    sendException);
        }
    }

    /**
     * 화자별 활성 세션 조회/생성.
     */
    private ActiveSttSession getOrCreateSttSession(long userId, String speakerName, long now) {
        ActiveSttSession existing = activeSttSessionsByUserId.get(userId);
        if (existing != null) {
            return existing;
        }

        String speakerId = Long.toString(userId);
        String normalizedSpeakerName = resolveSpeakerName(userId, speakerName);
        String sessionId = guildId + ":" + speakerId + ":" + now;

        ActiveSttSession created = new ActiveSttSession(sessionId, speakerId, now);
        ActiveSttSession previous = activeSttSessionsByUserId.putIfAbsent(userId, created);
        if (previous != null) {
            return previous;
        }

        try {
            // 세션 시작 시점에 결과 소비자(BotSttListener)를 함께 바인딩한다.
            BotSttListener listener = new BotSttListener(meetingId, speakerId, normalizedSpeakerName, captionChannel);
            sttProvider.startSession(sessionId, speakerId, listener);
            log.info(
                    "[STT/세션시작] guildId={}, meetingId={}, userId={}, sessionId={}, speakerName={}",
                    guildId,
                    meetingId,
                    userId,
                    sessionId,
                    normalizedSpeakerName);
            return created;
        } catch (Exception startException) {
            activeSttSessionsByUserId.remove(userId, created);
            log.warn(
                    "[STT/세션시작실패] guildId={}, meetingId={}, userId={}, sessionId={}",
                    guildId,
                    meetingId,
                    userId,
                    sessionId,
                    startException);
            return null;
        }
    }

    private String resolveSpeakerName(long userId, String fallbackSpeakerName) {
        if (guild != null) {
            Member member = guild.getMemberById(userId);
            if (member != null) {
                String effectiveName = member.getEffectiveName();
                if (effectiveName != null && !effectiveName.isBlank()) {
                    return effectiveName;
                }
                String userName = member.getUser().getName();
                if (userName != null && !userName.isBlank()) {
                    return userName;
                }
            }
        }

        if (fallbackSpeakerName != null && !fallbackSpeakerName.isBlank()) {
            return fallbackSpeakerName;
        }
        return "user-" + userId;
    }

    private void sendToActiveSession(ActiveSttSession session, long userId, byte[] pcm, long now) {
        session.lastAudioAtMs.set(now);
        sttProvider.sendPcm(session.sessionId, pcm, now);
    }

    /**
     * 무음 세션을 종료한다.
     *
     * @implNote 스케줄러가 주기적으로 검사하므로, 다른 사용자의 패킷 도착 여부와 무관하게 종료된다.
     */
    private void endInactiveSttSessionsOnSchedule() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, ActiveSttSession> entry : activeSttSessionsByUserId.entrySet()) {
            Long userId = entry.getKey();
            ActiveSttSession session = entry.getValue();
            long silenceMs = now - session.lastAudioAtMs.get();
            if (silenceMs < STT_SILENCE_END_MS) {
                continue;
            }

            if (activeSttSessionsByUserId.remove(userId, session)) {
                safelyEndSession(session, "silence_" + silenceMs + "ms");
            }
        }
    }

    /**
     * STT 세션 종료를 예외 안전하게 실행한다.
     */
    private void safelyEndSession(ActiveSttSession session, String reason) {
        try {
            sttProvider.endSession(session.sessionId);
            log.info(
                    "[STT/세션종료] guildId={}, meetingId={}, sessionId={}, speakerId={}, reason={}",
                    guildId,
                    meetingId,
                    session.sessionId,
                    session.speakerId,
                    reason);
        } catch (Exception endException) {
            log.warn(
                    "[STT/세션종료실패] guildId={}, meetingId={}, sessionId={}, reason={}",
                    guildId,
                    meetingId,
                    session.sessionId,
                    reason,
                    endException);
        }
    }

    /**
     * OpusPacket에서 PCM 바이트를 안전하게 추출한다.
     *
     * <p>주의:
     * - packet.canDecode()는 "디코더 존재/조건" 확인 성격이라,
     *   실제 프레임 복호화/디코딩이 항상 성공한다는 보장은 없다.
     * - 그래서 decode() 결과 null 여부를 직접 확인하고, null이면 프레임을 드롭한다.
     */
    private byte[] decodePcmSafely(OpusPacket packet, long userId) {
        try {
            short[] decodedShort = packet.decode();
            if (decodedShort == null || decodedShort.length == 0) {
                nonDecodableEncodedPackets.incrementAndGet();
                firstDecodeFailureReason.compareAndSet(null, "decodedShort is null/empty");
                long failures = decodeFailureCount.incrementAndGet();
                if (failures <= 3 || failures % 200 == 0) {
                    log.warn("[음성/빈PCM] guildId={}, userId={}, decodeFailures={}", guildId, userId, failures);
                }
                return null;
            }

            return OpusPacket.getAudioData(decodedShort, 1.0);
        } catch (Exception exception) {
            // 상위에서 카운트/로그 처리하도록 그대로 전달
            throw exception;
        }
    }

    private ForwardDecision evaluateVad(
            long userId, SpeakerStats stats, byte[] decodedPcm, SpeechEvidence speechEvidence, long now) {
        stats.preRollBuffer.add(decodedPcm);
        ActiveSttSession activeSession = activeSttSessionsByUserId.get(userId);
        long startThreshold = vadStartThreshold.get();
        long endThreshold = vadEndThreshold.get();
        boolean startSpeech = speechEvidence.webRtcAvailable
                ? speechEvidence.webRtcScore >= VAD_START_PROB_THRESHOLD
                : speechEvidence.rms >= startThreshold;
        boolean continueSpeech = speechEvidence.webRtcAvailable
                ? speechEvidence.webRtcScore >= VAD_END_PROB_THRESHOLD
                : speechEvidence.rms >= endThreshold;

        if (activeSession != null) {
            if (continueSpeech) {
                stats.vadState = VadState.IN_SPEECH;
                stats.trailingSilenceStartedAtMs.set(0L);
                return ForwardDecision.FORWARD;
            }
            if (stats.trailingSilenceStartedAtMs.get() == 0L) {
                stats.trailingSilenceStartedAtMs.set(now);
            }
            stats.vadState = VadState.TRAILING_SILENCE;
            return ForwardDecision.DROP;
        }

        if (startSpeech) {
            long consecutive = stats.consecutiveSpeechFrames.incrementAndGet();
            stats.vadState = VadState.SPEECH_CANDIDATE;
            if (consecutive >= VAD_MIN_CONSECUTIVE_SPEECH_FRAMES) {
                stats.consecutiveSpeechFrames.set(0L);
                stats.trailingSilenceStartedAtMs.set(0L);
                stats.vadState = VadState.IN_SPEECH;
                return ForwardDecision.START_WITH_PREROLL;
            }
            return ForwardDecision.DROP;
        }

        if (continueSpeech && stats.vadState == VadState.SPEECH_CANDIDATE) {
            return ForwardDecision.DROP;
        }

        stats.consecutiveSpeechFrames.set(0L);
        stats.trailingSilenceStartedAtMs.set(0L);
        stats.vadState = VadState.IDLE;
        return ForwardDecision.DROP;
    }

    private int calculateRms(byte[] decodedPcm) {
        if (decodedPcm == null || decodedPcm.length < 2) {
            return 0;
        }

        long sumSquare = 0L;
        int sampleCount = 0;
        // JDA 출력은 16-bit signed big-endian PCM
        for (int index = 0; index + 1 < decodedPcm.length; index += 2) {
            int high = decodedPcm[index] & 0xFF;
            int low = decodedPcm[index + 1] & 0xFF;
            short sample = (short) ((high << 8) | low);
            int value = sample;
            sumSquare += (long) value * value;
            sampleCount++;
        }

        if (sampleCount == 0) {
            return 0;
        }
        return (int) Math.sqrt(sumSquare / (double) sampleCount);
    }

    private SpeechEvidence detectSpeechEvidence(long userId, byte[] decodedPcm, int rms) {
        VAD vad = getOrCreateWebRtcVad(userId);
        if (vad == null) {
            return SpeechEvidence.rmsOnly(rms);
        }

        byte[] mono48kLe = toMono48kLe(decodedPcm);
        try {
            float score = vad.speechProbability(mono48kLe);
            return SpeechEvidence.webRtc(score, rms);
        } catch (Exception exception) {
            if (webRtcVadUnavailableLogged.compareAndSet(false, true)) {
                log.warn("[VAD/WEBRTC폴백] guildId={}, reason={}", guildId, exception.toString());
            }
            webRtcVadByUserId.remove(userId, vad);
            closeVadQuietly(vad);
            return SpeechEvidence.rmsOnly(rms);
        }
    }

    private VAD getOrCreateWebRtcVad(long userId) {
        VAD existing = webRtcVadByUserId.get(userId);
        if (existing != null) {
            return existing;
        }

        try {
            VAD created = new VAD();
            VAD previous = webRtcVadByUserId.putIfAbsent(userId, created);
            if (previous != null) {
                closeVadQuietly(created);
                return previous;
            }
            return created;
        } catch (Throwable throwable) {
            if (webRtcVadUnavailableLogged.compareAndSet(false, true)) {
                log.warn("[VAD/WEBRTC사용불가] guildId={}, fallback=RMS, reason={}", guildId, throwable.toString());
            }
            return null;
        }
    }

    private void closeVadQuietly(VAD vad) {
        try {
            vad.close();
        } catch (Exception ignored) {
            // no-op
        }
    }

    private byte[] toMono48kLe(byte[] stereoBe) {
        if (stereoBe == null || stereoBe.length < 4) {
            return new byte[0];
        }

        int stereoFrameSize = 4;
        int frameCount = stereoBe.length / stereoFrameSize;
        byte[] monoLe = new byte[frameCount * 2];
        int out = 0;
        for (int frame = 0; frame < frameCount; frame++) {
            int index = frame * stereoFrameSize;
            short left = (short) (((stereoBe[index] & 0xFF) << 8) | (stereoBe[index + 1] & 0xFF));
            short right = (short) (((stereoBe[index + 2] & 0xFF) << 8) | (stereoBe[index + 3] & 0xFF));
            short mono = (short) ((left + right) / 2);
            monoLe[out++] = (byte) (mono & 0xFF);
            monoLe[out++] = (byte) ((mono >>> 8) & 0xFF);
        }
        return monoLe;
    }

    private void maybeCalibrateVad(long now, int rms) {
        if (calibrationDone.get()) {
            return;
        }

        long startedAt = calibrationStartedAtMs.get();
        if (now - startedAt <= VAD_CALIBRATION_WINDOW_MS) {
            synchronized (calibrationNoiseRms) {
                if (rms >= vadStartThreshold.get()) {
                    calibrationSpeechRms.add(rms);
                } else {
                    calibrationNoiseRms.add(rms);
                }
            }
            return;
        }

        if (!calibrationDone.compareAndSet(false, true)) {
            return;
        }

        int[] noise;
        int[] speech;
        synchronized (calibrationNoiseRms) {
            noise = calibrationNoiseRms.stream().mapToInt(Integer::intValue).toArray();
            speech = calibrationSpeechRms.stream().mapToInt(Integer::intValue).toArray();
        }

        if (noise.length == 0) {
            log.info(
                    "[VAD/캘리브레이션] guildId={}, noise 샘플 부족으로 기본 임계값 유지(start={}, end={})",
                    guildId,
                    vadStartThreshold.get(),
                    vadEndThreshold.get());
            return;
        }

        Arrays.sort(noise);
        Arrays.sort(speech);
        int noiseP95 = percentile(noise, 95);
        int speechP50 = speech.length == 0 ? 0 : percentile(speech, 50);
        int speechP95 = speech.length == 0 ? 0 : percentile(speech, 95);

        long suggestedStart = Math.max(VAD_START_RMS_THRESHOLD, Math.round(noiseP95 * 1.8));
        long suggestedEnd = Math.max(VAD_END_RMS_THRESHOLD, Math.round(noiseP95 * 1.2));
        if (suggestedEnd >= suggestedStart) {
            suggestedEnd = Math.max(VAD_END_RMS_THRESHOLD, suggestedStart - 100);
        }

        vadStartThreshold.set(suggestedStart);
        vadEndThreshold.set(suggestedEnd);

        log.info(
                "[VAD/캘리브레이션완료] guildId={}, noiseP95={}, speechP50={}, speechP95={}, appliedStart={}, appliedEnd={}",
                guildId,
                noiseP95,
                speechP50,
                speechP95,
                suggestedStart,
                suggestedEnd);
    }

    private int percentile(int[] sortedValues, int percentile) {
        if (sortedValues.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.length) - 1;
        index = Math.max(0, Math.min(index, sortedValues.length - 1));
        return sortedValues[index];
    }

    /**
     * 화자별 활성 STT 세션 상태.
     */
    private static class ActiveSttSession {
        private final String sessionId;
        private final String speakerId;
        private final AtomicLong lastAudioAtMs = new AtomicLong();

        private ActiveSttSession(String sessionId, String speakerId, long startedAtMs) {
            this.sessionId = sessionId;
            this.speakerId = speakerId;
            this.lastAudioAtMs.set(startedAtMs);
        }
    }

    private static final class SilenceWatcherThreadFactory implements ThreadFactory {
        private final long guildId;

        private SilenceWatcherThreadFactory(long guildId) {
            this.guildId = guildId;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "stt-silence-watcher-guild-" + guildId);
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 화자별 누적 수신 통계.
     */
    private static class SpeakerStats {
        private volatile String userName;
        private final AtomicLong encodedPacketCount = new AtomicLong();
        private final AtomicLong encodedByteCount = new AtomicLong();
        private final AtomicLong decodedPcmPacketCount = new AtomicLong();
        private final AtomicLong decodedPcmByteCount = new AtomicLong();
        private final AtomicLong consecutiveSpeechFrames = new AtomicLong();
        private final AtomicLong trailingSilenceStartedAtMs = new AtomicLong();
        private final AtomicLong packetCount = new AtomicLong();
        private final AtomicLong byteCount = new AtomicLong();
        private final AtomicLong lastSeenAtMs = new AtomicLong();
        private final PreRollBuffer preRollBuffer = new PreRollBuffer(VAD_PREROLL_MS);
        private volatile VadState vadState = VadState.IDLE;

        private SpeakerStats(String userName) {
            this.userName = userName;
        }

        private void updateUserNameIfPlaceholder(String candidateName) {
            if (candidateName == null || candidateName.isBlank()) {
                return;
            }
            if (this.userName != null && this.userName.startsWith("user-")) {
                this.userName = candidateName;
            }
        }
    }

    private enum VadState {
        IDLE,
        SPEECH_CANDIDATE,
        IN_SPEECH,
        TRAILING_SILENCE
    }

    private enum ForwardDecision {
        DROP,
        FORWARD,
        START_WITH_PREROLL
    }

    private static final class SpeechEvidence {
        private final boolean webRtcAvailable;
        private final float webRtcScore;
        private final int rms;

        private SpeechEvidence(boolean webRtcAvailable, float webRtcScore, int rms) {
            this.webRtcAvailable = webRtcAvailable;
            this.webRtcScore = webRtcScore;
            this.rms = rms;
        }

        private static SpeechEvidence webRtc(float webRtcScore, int rms) {
            return new SpeechEvidence(true, webRtcScore, rms);
        }

        private static SpeechEvidence rmsOnly(int rms) {
            return new SpeechEvidence(false, 0f, rms);
        }
    }

    private static final class PreRollBuffer {
        private static final int SAMPLE_RATE = 48_000;
        private static final int CHANNELS = 2;
        private static final int BYTES_PER_SAMPLE = 2;

        private final int maxBytes;
        private final Deque<byte[]> frames = new ArrayDeque<>();
        private int totalBytes = 0;

        private PreRollBuffer(int preRollMs) {
            this.maxBytes = (int) ((long) SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE * preRollMs / 1000L);
        }

        private void add(byte[] frame) {
            byte[] copy = Arrays.copyOf(frame, frame.length);
            frames.addLast(copy);
            totalBytes += copy.length;

            while (totalBytes > maxBytes && !frames.isEmpty()) {
                byte[] removed = frames.removeFirst();
                totalBytes -= removed.length;
            }
        }

        private List<byte[]> drain() {
            List<byte[]> drained = new ArrayList<>(frames);
            frames.clear();
            totalBytes = 0;
            return drained;
        }
    }
}
