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
 *
 * <p>동작 요약:
 * - Discord Opus 패킷을 디코딩한 PCM 기준으로 로컬 VAD를 수행한다.
 * - 로컬 VAD가 발화 시작을 확정하면 세션을 열고 프리롤 + 현재 프레임을 전송한다.
 * - 마지막 전송 시각 기준으로 무음이 이어지면 세션을 종료한다.
 */
public class PerUserAudioReceiveHandler implements AudioReceiveHandler {
    private static final Logger log = LoggerFactory.getLogger(PerUserAudioReceiveHandler.class);

    // 로그 과다 방지를 위한 주기(패킷 샘플링 로그)
    private static final long LOG_INTERVAL_PACKETS = 50;
    // 복호화 품질 요약 로그 주기(ms)
    private static final long DECRYPT_METRICS_LOG_INTERVAL_MS = 60_000L;
    // final 품질 로그에서 "최근 복호화 실패"로 볼 시간 범위(ms)
    private static final long QUALITY_RECENT_FAILURE_WINDOW_MS = 1_500L;
    // 연속 실패 streak가 이 값 이상이면 burst로 본다.
    private static final long DECRYPT_BURST_STREAK_THRESHOLD = 5L;
    // 이 시간(ms) 동안 화자 오디오 전달이 없으면 해당 STT 세션을 종료한다.
    private static final long STT_SILENCE_END_MS = 1800L;
    // 무음 종료 감시 주기(ms)
    private static final long STT_SILENCE_WATCH_INTERVAL_MS = 250L;
    // WebRTC VAD 점수 임계값(시작은 엄격, 유지는 완화)
    private static final float VAD_START_PROB_THRESHOLD = 0.62f;
    private static final float VAD_END_PROB_THRESHOLD = 0.45f;
    // 발화 시작 확정을 위한 연속 프레임 수
    private static final int VAD_MIN_CONSECUTIVE_SPEECH_FRAMES = 3;
    // 발화 시작 시 앞부분 유실을 줄이기 위한 프리롤 길이(ms)
    private static final int VAD_PREROLL_MS = 400;

    private final long guildId;
    private final long meetingId;
    private final SttProvider sttProvider;
    private final ScheduledExecutorService silenceWatcher;
    private final ScheduledExecutorService sttIoExecutor;
    private final Guild guild;
    private volatile MessageChannel captionChannel;

    // 사용자 ID별 누적 통계(튜닝/디버깅 지표)
    private final Map<Long, SpeakerStats> statsByUserId = new ConcurrentHashMap<>();
    // 사용자별 WebRTC VAD 인스턴스(네이티브 리소스)
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
    // final 품질 로그는 화자별 비교가 필요하므로 사용자별 실패 수를 별도로 누적한다.
    private final Map<Long, AtomicLong> decodeFailureCountByUserId = new ConcurrentHashMap<>();
    // 발화 시작/종료 주변에 실패가 몰렸는지 보기 위한 최근 실패 타임라인.
    private final Map<Long, RecentDecodeFailureTracker> recentDecodeFailuresByUserId = new ConcurrentHashMap<>();

    // 첫 디코딩 실패 사유를 기록해 원인 파악에 사용
    private final AtomicReference<String> firstDecodeFailureReason = new AtomicReference<>();

    // 첫 수신 순간 강조 로그 플래그(연결 확인용)
    private final AtomicBoolean firstEncodedLogged = new AtomicBoolean(false);
    private final AtomicBoolean firstUserLogged = new AtomicBoolean(false);
    private final AtomicBoolean webRtcVadUnavailableLogged = new AtomicBoolean(false);
    private final AtomicBoolean webRtcVadEnabledLogged = new AtomicBoolean(false);
    private final DecryptWindowMetrics decryptWindowMetrics = new DecryptWindowMetrics();

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
        this.sttIoExecutor = Executors.newSingleThreadScheduledExecutor(new SttIoThreadFactory(guildId));
        // 무음 종료 감시 스케줄러 시작
        this.silenceWatcher.scheduleAtFixedRate(
                this::endInactiveSttSessionsOnSchedule,
                STT_SILENCE_WATCH_INTERVAL_MS,
                STT_SILENCE_WATCH_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    public void updateCaptionChannel(MessageChannel captionChannel) {
        // 런타임 중 자막 채널이 바뀔 수 있으므로 setter로 주입
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
        decryptWindowMetrics.recordPacket(now);

        // 화자별 통계를 미리 준비한다. UserAudio가 아직 안 오면 user-<id> placeholder를 사용한다.
        SpeakerStats stats = statsByUserId.computeIfAbsent(userId, ignored -> new SpeakerStats("user-" + userId));
        long userEncodedPackets = stats.encodedPacketCount.incrementAndGet();
        stats.encodedByteCount.addAndGet(packet.getOpusAudio().length);
        stats.lastSeenAtMs.set(now);

        boolean canDecode = packet.canDecode();
        if (!canDecode) {
            // canDecode=false 프레임은 STT 전송 대상에서 제외
            nonDecodableEncodedPackets.incrementAndGet();
            recordDecodeFailureEvent(userId, now, DecodeFailureType.CANNOT_DECODE);
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
                    nonDecodableEncodedPackets.incrementAndGet();
                    long failures = recordDecodeFailureEvent(userId, now, DecodeFailureType.EMPTY_PCM);
                    if (failures <= 3 || failures % 200 == 0) {
                        log.warn("[음성/빈PCM] guildId={}, userId={}, decodeFailures={}", guildId, userId, failures);
                    }
                    return;
                }
                decryptWindowMetrics.recordSuccess();
                decodedFromEncodedPacketCount.incrementAndGet();
                decodedFromEncodedByteCount.addAndGet(decodedPcm.length);
                stats.decodedPcmPacketCount.incrementAndGet();
                stats.decodedPcmByteCount.addAndGet(decodedPcm.length);

                // 발화 판단 입력값(WebRTC-only)
                SpeechEvidence speechEvidence = detectSpeechEvidence(userId, decodedPcm);

                // 로컬 VAD 상태머신 결과를 기준으로 전송 여부를 결정한다.
                ForwardDecision decision = evaluateVad(userId, stats, decodedPcm, speechEvidence, now);
                if (decision == ForwardDecision.DROP) {
                    // no-op
                } else if (decision == ForwardDecision.START_WITH_PREROLL) {
                    // 시작 확정 시 세션을 만들고 프리롤 프레임을 먼저 전송한다.
                    ActiveSttSession session = getOrCreateSttSession(userId, stats.userName, now);
                    if (session == null) {
                        // no-op
                    } else {
                        session.recordVadScore(speechEvidence.webRtcScore);
                        session.lastAudioAtMs.set(now);
                        for (byte[] preRollFrame : stats.preRollBuffer.drain()) {
                            submitSttSendTask(session, userId, preRollFrame, now);
                        }
                    }
                } else {
                    // 이미 발화 중이면 현재 프레임을 바로 전송한다.
                    forwardPcmToStt(userId, stats.userName, decodedPcm, speechEvidence.webRtcScore, now);
                }
            } catch (Exception decodeException) {
                nonDecodableEncodedPackets.incrementAndGet();
                firstDecodeFailureReason.compareAndSet(null, decodeException.getMessage());
                long failures = recordDecodeFailureEvent(userId, now, DecodeFailureType.EXCEPTION);
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

        maybeLogDecryptMetrics(now);
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
        // 명시 종료 시에는 감시 스레드부터 멈춘다.
        silenceWatcher.shutdownNow();
        for (Map.Entry<Long, ActiveSttSession> entry : activeSttSessionsByUserId.entrySet()) {
            Long userId = entry.getKey();
            ActiveSttSession activeSession = entry.getValue();
            if (activeSttSessionsByUserId.remove(userId, activeSession)) {
                // map에서 제거 성공한 세션만 종료해 중복 종료를 막는다.
                endSessionNow(activeSession, "manual_close_all");
            }
        }
        // STT I/O 전용 스레드도 함께 정리
        sttIoExecutor.shutdownNow();
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
    private void forwardPcmToStt(long userId, String speakerName, byte[] decodedPcm, float vadScore, long now) {
        // 세션이 없으면 생성(start), 있으면 재사용
        ActiveSttSession session = getOrCreateSttSession(userId, speakerName, now);
        if (session == null) {
            return;
        }

        session.recordVadScore(vadScore);
        session.lastAudioAtMs.set(now);
        submitSttSendTask(session, userId, decodedPcm, now);
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
        // 세션 ID는 길드/화자/시작시각 조합으로 생성한다.
        String sessionId = guildId + ":" + speakerId + ":" + now;

        // 세션 시작 시점의 복호화 상태를 고정해 final 시점과 비교한다.
        long decodeFailuresAtStart = decodeFailuresForUser(userId);
        long recentDecodeFailuresAtStart = recentDecodeFailuresForUser(userId, now);
        ActiveSttSession created =
                new ActiveSttSession(sessionId, speakerId, now, decodeFailuresAtStart, recentDecodeFailuresAtStart);
        ActiveSttSession previous = activeSttSessionsByUserId.putIfAbsent(userId, created);
        if (previous != null) {
            return previous;
        }

        if (!submitSttStartTask(userId, created, normalizedSpeakerName)) {
            activeSttSessionsByUserId.remove(userId, created);
            return null;
        }
        return created;
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
        ActiveSttSession current = activeSttSessionsByUserId.get(userId);
        if (current != session) {
            // 세션 교체 경합으로 늦게 도착한 작업이면 무시
            return;
        }
        session.recordForwardedPcm(pcm.length);
        session.lastAudioAtMs.set(now);
        sttProvider.sendPcm(session.sessionId, pcm, now);
    }

    private boolean submitSttStartTask(long userId, ActiveSttSession session, String speakerName) {
        try {
            sttIoExecutor.execute(() -> {
                try {
                    // 세션 시작 시 화자/미팅 컨텍스트를 리스너에 고정한다.
                    BotSttListener listener = new BotSttListener(
                            meetingId,
                            session.speakerId,
                            speakerName,
                            captionChannel,
                            () -> buildQualitySnapshot(userId, session));
                    sttProvider.startSession(session.sessionId, session.speakerId, listener);
                    log.info(
                            "[STT/세션시작] guildId={}, meetingId={}, userId={}, sessionId={}, speakerName={}",
                            guildId,
                            meetingId,
                            userId,
                            session.sessionId,
                            speakerName);
                } catch (Exception startException) {
                    activeSttSessionsByUserId.remove(userId, session);
                    log.warn(
                            "[STT/세션시작실패] guildId={}, meetingId={}, userId={}, sessionId={}",
                            guildId,
                            meetingId,
                            userId,
                            session.sessionId,
                            startException);
                }
            });
            return true;
        } catch (Exception queueException) {
            log.warn(
                    "[STT/시작큐등록실패] guildId={}, meetingId={}, userId={}, sessionId={}",
                    guildId,
                    meetingId,
                    userId,
                    session.sessionId,
                    queueException);
            return false;
        }
    }

    private void submitSttSendTask(ActiveSttSession session, long userId, byte[] pcm, long now) {
        try {
            sttIoExecutor.execute(() -> {
                try {
                    // 네트워크 I/O를 수신 스레드와 분리해 패킷 처리 지연을 줄인다.
                    sendToActiveSession(session, userId, pcm, now);
                } catch (Exception sendException) {
                    log.warn(
                            "[STT/PCM전달실패] guildId={}, sessionId={}, userId={}",
                            guildId,
                            session.sessionId,
                            userId,
                            sendException);
                }
            });
        } catch (Exception queueException) {
            log.warn(
                    "[STT/전송큐등록실패] guildId={}, sessionId={}, userId={}",
                    guildId,
                    session.sessionId,
                    userId,
                    queueException);
        }
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
            // 마지막 오디오 전송 시각 기준으로 무음 지속 시간을 계산
            long silenceMs = now - session.lastAudioAtMs.get();
            if (silenceMs < STT_SILENCE_END_MS) {
                continue;
            }

            if (activeSttSessionsByUserId.remove(userId, session)) {
                // 종료 경합 방지를 위해 제거 성공한 세션만 종료한다.
                safelyEndSession(session, "silence_" + silenceMs + "ms");
            }
        }
    }

    /**
     * STT 세션 종료를 예외 안전하게 실행한다.
     */
    private void safelyEndSession(ActiveSttSession session, String reason) {
        submitSttEndTask(session, reason);
    }

    private void submitSttEndTask(ActiveSttSession session, String reason) {
        try {
            // 종료도 비동기 큐에서 처리해 수신 루프를 막지 않는다.
            sttIoExecutor.execute(() -> endSessionNow(session, reason));
        } catch (Exception queueException) {
            log.warn(
                    "[STT/종료큐등록실패] guildId={}, meetingId={}, sessionId={}, reason={}",
                    guildId,
                    meetingId,
                    session.sessionId,
                    reason,
                    queueException);
        }
    }

    private void endSessionNow(ActiveSttSession session, String reason) {
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

    private BotSttListener.SessionQualitySnapshot buildQualitySnapshot(long userId, ActiveSttSession session) {
        long now = System.currentTimeMillis();
        long decodeFailuresAtFinal = decodeFailuresForUser(userId);
        long recentDecodeFailuresAtFinal = recentDecodeFailuresForUser(userId, now);
        return session.toQualitySnapshot(decodeFailuresAtFinal, recentDecodeFailuresAtFinal);
    }

    private long recordDecodeFailureEvent(long userId, long now, DecodeFailureType failureType) {
        // 전역 요약 로그와 final 품질 로그가 같은 실패 이벤트를 바라보도록 한 곳에서 기록한다.
        decryptWindowMetrics.recordFailure(now, failureType);
        long failures = decodeFailureCount.incrementAndGet();
        decodeFailureCountByUserId
                .computeIfAbsent(userId, ignored -> new AtomicLong())
                .incrementAndGet();
        recentDecodeFailuresByUserId
                .computeIfAbsent(userId, ignored -> new RecentDecodeFailureTracker())
                .record(now);
        return failures;
    }

    private long decodeFailuresForUser(long userId) {
        AtomicLong counter = decodeFailureCountByUserId.get(userId);
        return counter == null ? 0L : counter.get();
    }

    private long recentDecodeFailuresForUser(long userId, long now) {
        RecentDecodeFailureTracker tracker = recentDecodeFailuresByUserId.get(userId);
        if (tracker == null) {
            return 0L;
        }
        return tracker.countSince(now - QUALITY_RECENT_FAILURE_WINDOW_MS);
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
                firstDecodeFailureReason.compareAndSet(null, "decodedShort is null/empty");
                return null;
            }

            // Opus decode 결과(short[])를 byte[] PCM으로 변환
            return OpusPacket.getAudioData(decodedShort, 1.0);
        } catch (Exception exception) {
            // 상위에서 카운트/로그 처리하도록 그대로 전달
            throw exception;
        }
    }

    private ForwardDecision evaluateVad(
            long userId, SpeakerStats stats, byte[] decodedPcm, SpeechEvidence speechEvidence, long now) {
        // 시작 직전 유실 방지를 위해 프리롤 버퍼는 항상 유지
        stats.preRollBuffer.add(decodedPcm);
        ActiveSttSession activeSession = activeSttSessionsByUserId.get(userId);
        if (!speechEvidence.webRtcAvailable) {
            // webrtc-only: WebRTC VAD를 사용할 수 없는 프레임은 발화 판단에 사용하지 않는다.
            // 활성 세션이 있어도 이 프레임은 전송하지 않고, silence watcher가 종료를 처리한다.
            stats.vadState = activeSession == null ? VadState.IDLE : VadState.TRAILING_SILENCE;
            return ForwardDecision.DROP;
        }
        boolean startSpeech = speechEvidence.webRtcScore >= VAD_START_PROB_THRESHOLD;
        boolean continueSpeech = speechEvidence.webRtcScore >= VAD_END_PROB_THRESHOLD;

        if (activeSession != null) {
            if (continueSpeech) {
                // 이미 발화 중이며 유지 조건 충족 -> 전달 계속
                stats.vadState = VadState.IN_SPEECH;
                stats.trailingSilenceStartedAtMs.set(0L);
                return ForwardDecision.FORWARD;
            }
            // 발화 중이지만 유지 조건 미달 -> 무음 상태로 전이
            // (실제 세션 종료는 silence watcher가 담당)
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
                // 연속 프레임 조건 충족 시 발화 시작 확정
                stats.consecutiveSpeechFrames.set(0L);
                stats.trailingSilenceStartedAtMs.set(0L);
                stats.vadState = VadState.IN_SPEECH;
                return ForwardDecision.START_WITH_PREROLL;
            }
            // 시작 후보 상태에선 아직 전송하지 않고 다음 프레임을 본다.
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

    private SpeechEvidence detectSpeechEvidence(long userId, byte[] decodedPcm) {
        VAD vad = getOrCreateWebRtcVad(userId);
        if (vad == null) {
            // WebRTC VAD를 사용할 수 없으면(네이티브 로딩 실패 등) 이 프레임은 VAD 판단에서 제외한다.
            // RMS 폴백은 사용하지 않는다(webrtc-only).
            return SpeechEvidence.unavailable();
        }

        byte[] mono48kLe = toMono48kLe(decodedPcm);
        try {
            float score = vad.speechProbability(mono48kLe);
            if (webRtcVadEnabledLogged.compareAndSet(false, true)) {
                log.info(
                        "[VAD/WEBRTC활성] guildId={}, startProbThreshold={}, endProbThreshold={}, arch={}",
                        guildId,
                        VAD_START_PROB_THRESHOLD,
                        VAD_END_PROB_THRESHOLD,
                        System.getProperty("os.arch"));
            }
            return SpeechEvidence.webRtc(score);
        } catch (Exception exception) {
            if (webRtcVadUnavailableLogged.compareAndSet(false, true)) {
                log.warn("[VAD/WEBRTC실패] guildId={}, reason={}", guildId, exception.toString());
            }
            // 반복 실패를 피하기 위해 해당 인스턴스를 폐기한다.
            webRtcVadByUserId.remove(userId, vad);
            closeVadQuietly(vad);
            return SpeechEvidence.unavailable();
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
                log.warn("[VAD/WEBRTC사용불가] guildId={}, reason={}", guildId, throwable.toString());
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

        // WebRTC VAD 입력 포맷:
        // - mono
        // - little-endian pcm16
        // 현재 입력(JDA)은 48kHz stereo big-endian 이므로 변환이 필요하다.
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

    /**
     * 화자별 활성 STT 세션 상태.
     */
    private static class ActiveSttSession {
        private final String sessionId;
        private final String speakerId;
        private final long decodeFailuresAtStart;
        private final long recentDecodeFailuresAtStart;
        // OpenAI로 넘기려고 큐에 태운 PCM 프레임/바이트 수.
        private final AtomicLong forwardedFrameCount = new AtomicLong();
        private final AtomicLong forwardedRawPcmBytes = new AtomicLong();
        // VAD 점수는 final 품질 로그에서 "얼마나 확실한 발화였는지" 보는 관측값이다.
        private final Object vadScoreLock = new Object();
        private float maxVadScore = 0.0f;
        private double totalVadScore = 0.0d;
        private long vadScoreCount = 0L;
        // 마지막으로 STT에 PCM을 보낸 시각.
        // silence watcher가 이 값을 기준으로 종료 시점을 계산한다.
        private final AtomicLong lastAudioAtMs = new AtomicLong();

        private ActiveSttSession(
                String sessionId,
                String speakerId,
                long startedAtMs,
                long decodeFailuresAtStart,
                long recentDecodeFailuresAtStart) {
            this.sessionId = sessionId;
            this.speakerId = speakerId;
            this.decodeFailuresAtStart = decodeFailuresAtStart;
            this.recentDecodeFailuresAtStart = recentDecodeFailuresAtStart;
            this.lastAudioAtMs.set(startedAtMs);
        }

        private void recordForwardedPcm(int pcmBytes) {
            forwardedFrameCount.incrementAndGet();
            forwardedRawPcmBytes.addAndGet(pcmBytes);
        }

        private void recordVadScore(float vadScore) {
            if (Float.isNaN(vadScore) || Float.isInfinite(vadScore)) {
                return;
            }
            synchronized (vadScoreLock) {
                maxVadScore = Math.max(maxVadScore, vadScore);
                totalVadScore += vadScore;
                vadScoreCount++;
            }
        }

        private BotSttListener.SessionQualitySnapshot toQualitySnapshot(
                long decodeFailuresAtFinal, long recentDecodeFailuresAtFinal) {
            float currentMaxVadScore;
            float currentAvgVadScore;
            synchronized (vadScoreLock) {
                currentMaxVadScore = maxVadScore;
                currentAvgVadScore = vadScoreCount == 0 ? 0.0f : (float) (totalVadScore / vadScoreCount);
            }
            return new BotSttListener.SessionQualitySnapshot(
                    forwardedFrameCount.get(),
                    forwardedRawPcmBytes.get(),
                    decodeFailuresAtStart,
                    decodeFailuresAtFinal,
                    recentDecodeFailuresAtStart,
                    recentDecodeFailuresAtFinal,
                    currentMaxVadScore,
                    currentAvgVadScore);
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

    private static final class SttIoThreadFactory implements ThreadFactory {
        private final long guildId;

        private SttIoThreadFactory(long guildId) {
            this.guildId = guildId;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "stt-io-guild-" + guildId);
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
        // 발화 시작 확정 전 연속 프레임 카운터
        private final AtomicLong consecutiveSpeechFrames = new AtomicLong();
        // 로컬 VAD 상태 관측용(종료 판단은 silence watcher가 최종 담당)
        private final AtomicLong trailingSilenceStartedAtMs = new AtomicLong();
        private final AtomicLong packetCount = new AtomicLong();
        private final AtomicLong byteCount = new AtomicLong();
        private final AtomicLong lastSeenAtMs = new AtomicLong();
        // 발화 시작 시 프리롤(앞부분) 전송을 위한 버퍼
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

        private SpeechEvidence(boolean webRtcAvailable, float webRtcScore) {
            this.webRtcAvailable = webRtcAvailable;
            this.webRtcScore = webRtcScore;
        }

        private static SpeechEvidence webRtc(float webRtcScore) {
            return new SpeechEvidence(true, webRtcScore);
        }

        private static SpeechEvidence unavailable() {
            return new SpeechEvidence(false, 0f);
        }
    }

    private void maybeLogDecryptMetrics(long now) {
        String summary = decryptWindowMetrics.snapshotAndRotateIfDue(now);
        if (summary == null) {
            return;
        }
        log.info("[복호화/요약] guildId={}, {}", guildId, summary);
    }

    private enum DecodeFailureType {
        CANNOT_DECODE,
        EMPTY_PCM,
        EXCEPTION
    }

    private static final class RecentDecodeFailureTracker {
        private final Deque<Long> failureTimesMs = new ArrayDeque<>();

        synchronized void record(long nowMs) {
            failureTimesMs.addLast(nowMs);
            pruneOlderThan(nowMs - QUALITY_RECENT_FAILURE_WINDOW_MS);
        }

        synchronized long countSince(long cutoffMs) {
            pruneOlderThan(cutoffMs);
            return failureTimesMs.size();
        }

        private void pruneOlderThan(long cutoffMs) {
            while (!failureTimesMs.isEmpty() && failureTimesMs.peekFirst() < cutoffMs) {
                failureTimesMs.removeFirst();
            }
        }
    }

    private static final class DecryptWindowMetrics {
        private long windowStartedAtMs = System.currentTimeMillis();
        private long totalPackets = 0L;
        private long totalFailures = 0L;
        private long cannotDecodeFailures = 0L;
        private long emptyPcmFailures = 0L;
        private long exceptionFailures = 0L;
        private long currentFailureStreak = 0L;
        private long maxFailureStreak = 0L;
        private long burstCount = 0L;

        synchronized void recordPacket(long nowMs) {
            rotateIfNeeded(nowMs);
            totalPackets++;
        }

        synchronized void recordSuccess() {
            if (currentFailureStreak >= DECRYPT_BURST_STREAK_THRESHOLD) {
                burstCount++;
            }
            currentFailureStreak = 0L;
        }

        synchronized void recordFailure(long nowMs, DecodeFailureType failureType) {
            rotateIfNeeded(nowMs);
            totalFailures++;
            currentFailureStreak++;
            maxFailureStreak = Math.max(maxFailureStreak, currentFailureStreak);
            if (failureType == DecodeFailureType.CANNOT_DECODE) {
                cannotDecodeFailures++;
                return;
            }
            if (failureType == DecodeFailureType.EMPTY_PCM) {
                emptyPcmFailures++;
                return;
            }
            exceptionFailures++;
        }

        synchronized String snapshotAndRotateIfDue(long nowMs) {
            if (nowMs - windowStartedAtMs < DECRYPT_METRICS_LOG_INTERVAL_MS) {
                return null;
            }
            String summary = buildSummary(nowMs);
            reset(nowMs);
            return summary;
        }

        private void rotateIfNeeded(long nowMs) {
            if (nowMs - windowStartedAtMs < DECRYPT_METRICS_LOG_INTERVAL_MS) {
                return;
            }
            reset(nowMs);
        }

        private String buildSummary(long nowMs) {
            long windowMs = Math.max(1L, nowMs - windowStartedAtMs);
            double failureRate = totalPackets == 0 ? 0.0 : (totalFailures * 100.0) / totalPackets;
            return "windowMs=" + windowMs
                    + ", packets=" + totalPackets
                    + ", failures=" + totalFailures
                    + ", failureRate=" + String.format("%.2f", failureRate) + "%"
                    + ", cannotDecode=" + cannotDecodeFailures
                    + ", emptyPcm=" + emptyPcmFailures
                    + ", exceptions=" + exceptionFailures
                    + ", maxFailureStreak=" + maxFailureStreak
                    + ", burstCount=" + burstCount;
        }

        private void reset(long nowMs) {
            windowStartedAtMs = nowMs;
            totalPackets = 0L;
            totalFailures = 0L;
            cannotDecodeFailures = 0L;
            emptyPcmFailures = 0L;
            exceptionFailures = 0L;
            currentFailureStreak = 0L;
            maxFailureStreak = 0L;
            burstCount = 0L;
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
            // 원본 배열은 이후 재사용될 수 있으므로 복사본을 저장한다.
            byte[] copy = Arrays.copyOf(frame, frame.length);
            frames.addLast(copy);
            totalBytes += copy.length;

            while (totalBytes > maxBytes && !frames.isEmpty()) {
                byte[] removed = frames.removeFirst();
                totalBytes -= removed.length;
            }
        }

        private List<byte[]> drain() {
            // 시작 시점에만 한 번 꺼내 전송하고 즉시 비운다.
            List<byte[]> drained = new ArrayList<>(frames);
            frames.clear();
            totalBytes = 0;
            return drained;
        }
    }
}
