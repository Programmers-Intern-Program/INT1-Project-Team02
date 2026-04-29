package com.flodiback.bot.audio;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.bot.stt.BotSttListener;
import com.flodiback.domain.speech.stt.SttProvider;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.OpusPacket;
import net.dv8tion.jda.api.audio.UserAudio;

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
    private static final long STT_SILENCE_END_MS = 1500L;

    private final long guildId;
    private final long meetingId;
    private final SttProvider sttProvider;

    // 사용자 ID별 누적 통계
    private final Map<Long, SpeakerStats> statsByUserId = new ConcurrentHashMap<>();
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

    public PerUserAudioReceiveHandler(long guildId, SttProvider sttProvider, long meetingId) {
        this.guildId = guildId;
        this.sttProvider = Objects.requireNonNull(sttProvider);
        this.meetingId = meetingId;
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

                // 디코딩 성공 시점에만 STT로 실제 PCM을 전달한다.
                forwardPcmToStt(userId, stats.userName, decodedPcm, now);
            } catch (Exception decodeException) {
                nonDecodableEncodedPackets.incrementAndGet();
                firstDecodeFailureReason.compareAndSet(null, decodeException.getMessage());
                long failures = decodeFailureCount.incrementAndGet();
                // 실시간 수신 루프에서 매 프레임 warn을 찍으면 로그가 폭증하므로 샘플링한다.
                if (failures <= 3 || failures % 200 == 0) {
                    log.warn(
                            "Failed to decode opus packet. guildId={}, userId={}, decodeFailures={}",
                            guildId,
                            userId,
                            failures,
                            decodeException);
                }
            }
        }

        // 현재 화자 외 세션 중 무음 초과 세션을 정리한다.
        endInactiveSttSessions(now, userId);

        if (firstEncodedLogged.compareAndSet(false, true)) {
            log.info(
                    "[FIRST_ENCODED_AUDIO] guildId={}, firstUserId={}, encodedPackets={}",
                    guildId,
                    userId,
                    encodedCount);
        }
        if (encodedCount == 1 || encodedCount % LOG_INTERVAL_PACKETS == 0) {
            log.info(
                    "Encoded audio received. guildId={}, encodedPackets={}, userId={}, userEncodedPackets={}, canDecode={}",
                    guildId,
                    encodedCount,
                    userId,
                    userEncodedPackets,
                    canDecode);
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
                    "[FIRST_USER_AUDIO] guildId={}, userId={}, userName={}, frameBytes={}",
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
                    "Audio receive stats. guildId={}, userId={}, userName={}, userPackets={}, bytes={}, totalUserPackets={}",
                    guildId,
                    userId,
                    userName,
                    packetCount,
                    stats.byteCount.get(),
                    totalUserPackets);
        }
    }

    /**
     * 길드 음성 세션을 명시적으로 종료할 때(예: !leave) 모든 STT 세션을 end(commit)한다.
     */
    public void closeAllSttSessions() {
        for (Map.Entry<Long, ActiveSttSession> entry : activeSttSessionsByUserId.entrySet()) {
            Long userId = entry.getKey();
            ActiveSttSession activeSession = entry.getValue();
            if (activeSttSessionsByUserId.remove(userId, activeSession)) {
                safelyEndSession(activeSession, "manual_close_all");
            }
        }
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
                            .append(stats.byteCount.get());
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
            sttProvider.sendPcm(session.sessionId, decodedPcm, now);
        } catch (Exception sendException) {
            log.warn(
                    "Failed to send PCM to STT. guildId={}, sessionId={}, userId={}",
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
        String normalizedSpeakerName = (speakerName == null || speakerName.isBlank()) ? "user-" + userId : speakerName;
        String sessionId = guildId + ":" + speakerId + ":" + now;

        ActiveSttSession created = new ActiveSttSession(sessionId, speakerId, now);
        ActiveSttSession previous = activeSttSessionsByUserId.putIfAbsent(userId, created);
        if (previous != null) {
            return previous;
        }

        try {
            // 세션 시작 시점에 결과 소비자(BotSttListener)를 함께 바인딩한다.
            BotSttListener listener = new BotSttListener(meetingId, speakerId, normalizedSpeakerName);
            sttProvider.startSession(sessionId, speakerId, listener);
            log.info(
                    "STT session started. guildId={}, meetingId={}, userId={}, sessionId={}, speakerName={}",
                    guildId,
                    meetingId,
                    userId,
                    sessionId,
                    normalizedSpeakerName);
            return created;
        } catch (Exception startException) {
            activeSttSessionsByUserId.remove(userId, created);
            log.warn(
                    "Failed to start STT session. guildId={}, meetingId={}, userId={}, sessionId={}",
                    guildId,
                    meetingId,
                    userId,
                    sessionId,
                    startException);
            return null;
        }
    }

    /**
     * 무음 세션을 종료한다.
     *
     * @param now 현재 시각(ms)
     * @param speakingUserId 지금 오디오가 들어온 사용자 ID(해당 사용자는 종료 제외)
     * @implNote 현재 구현은 "오디오 패킷이 들어올 때" 무음 체크를 수행한다.
     *     따라서 완전 무음 상태에서는 다음 패킷 또는 !leave 시점에 종료가 반영된다.
     */
    private void endInactiveSttSessions(long now, long speakingUserId) {
        for (Map.Entry<Long, ActiveSttSession> entry : activeSttSessionsByUserId.entrySet()) {
            Long userId = entry.getKey();
            if (userId == speakingUserId) {
                continue;
            }

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
                    "STT session ended. guildId={}, meetingId={}, sessionId={}, speakerId={}, reason={}",
                    guildId,
                    meetingId,
                    session.sessionId,
                    session.speakerId,
                    reason);
        } catch (Exception endException) {
            log.warn(
                    "Failed to end STT session. guildId={}, meetingId={}, sessionId={}, reason={}",
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
                    log.warn(
                            "Decoded short PCM is empty. guildId={}, userId={}, decodeFailures={}",
                            guildId,
                            userId,
                            failures);
                }
                return null;
            }

            return OpusPacket.getAudioData(decodedShort, 1.0);
        } catch (Exception exception) {
            // 상위에서 카운트/로그 처리하도록 그대로 전달
            throw exception;
        }
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

    /**
     * 화자별 누적 수신 통계.
     */
    private static class SpeakerStats {
        private volatile String userName;
        private final AtomicLong encodedPacketCount = new AtomicLong();
        private final AtomicLong encodedByteCount = new AtomicLong();
        private final AtomicLong decodedPcmPacketCount = new AtomicLong();
        private final AtomicLong decodedPcmByteCount = new AtomicLong();
        private final AtomicLong packetCount = new AtomicLong();
        private final AtomicLong byteCount = new AtomicLong();
        private final AtomicLong lastSeenAtMs = new AtomicLong();

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
}
