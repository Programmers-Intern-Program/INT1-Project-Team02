package com.flodiback.bot.audio;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.OpusPacket;
import net.dv8tion.jda.api.audio.UserAudio;

/**
 * 화자별(User 단위) 오디오 수신 통계를 누적하는 핸들러.
 *
 * <p>현재 단계에서는 STT 전송 대신 "화자 분리 수신이 되는지"를 수치로 확인하는 목적이다.
 */
public class PerUserAudioReceiveHandler implements AudioReceiveHandler {
    private static final Logger log = LoggerFactory.getLogger(PerUserAudioReceiveHandler.class);
    // 로그가 너무 많아지는 것을 막기 위한 샘플링 주기
    private static final long LOG_INTERVAL_PACKETS = 50;

    private final long guildId;
    // 사용자 ID별 누적 통계 저장소
    private final Map<Long, SpeakerStats> statsByUserId = new ConcurrentHashMap<>();
    // 디코딩 전 Opus 패킷 수
    private final AtomicLong encodedPacketCount = new AtomicLong();
    // 화자 단위 패킷 수
    private final AtomicLong userPacketCount = new AtomicLong();
    // Opus 패킷 중 디코딩 가능한 수
    private final AtomicLong decodableEncodedPacketCount = new AtomicLong();
    // Opus에서 PCM으로 실제 디코딩한 패킷/바이트 수
    private final AtomicLong decodedFromEncodedPacketCount = new AtomicLong();
    private final AtomicLong decodedFromEncodedByteCount = new AtomicLong();
    // 디코드 가능 여부 추적
    private final AtomicLong nonDecodableEncodedPackets = new AtomicLong();
    // 첫 디코딩 실패 원인(예: No decoder available, Packet is not in order)
    private final AtomicReference<String> firstDecodeFailureReason = new AtomicReference<>();
    // 첫 수신 순간을 한번만 강조 로그로 남기기 위한 플래그
    private final AtomicBoolean firstEncodedLogged = new AtomicBoolean(false);
    private final AtomicBoolean firstUserLogged = new AtomicBoolean(false);

    public PerUserAudioReceiveHandler(long guildId) {
        this.guildId = guildId;
    }

    @Override
    public boolean canReceiveUser() {
        // true면 JDA가 사용자 단위 오디오 프레임을 handleUserAudio로 전달한다.
        return true;
    }

    @Override
    public boolean canReceiveEncoded() {
        // 인코딩(Opus) 레벨에서도 수신을 받아 디코딩 문제 여부를 빠르게 확인한다.
        return true;
    }

    @Override
    public void handleEncodedAudio(OpusPacket packet) {
        long encodedCount = encodedPacketCount.incrementAndGet();
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
        }

        long userId = packet.getUserId();
        SpeakerStats stats = statsByUserId.computeIfAbsent(userId, ignored -> new SpeakerStats("user-" + userId));
        long userEncodedPackets = stats.encodedPacketCount.incrementAndGet();
        stats.encodedByteCount.addAndGet(packet.getOpusAudio().length);
        stats.lastSeenAtMs.set(System.currentTimeMillis());

        if (canDecode) {
            byte[] decoded = packet.getAudioData(1.0);
            decodedFromEncodedPacketCount.incrementAndGet();
            decodedFromEncodedByteCount.addAndGet(decoded.length);
            stats.decodedPcmPacketCount.incrementAndGet();
            stats.decodedPcmByteCount.addAndGet(decoded.length);
        }

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

    @Override
    public void handleUserAudio(UserAudio userAudio) {
        // 수신 프레임에서 화자 ID/이름/바이트 크기를 추출
        long userId = userAudio.getUser().getIdLong();
        String userName = userAudio.getUser().getName();
        int frameBytes = userAudio.getAudioData(1.0).length;
        if (firstUserLogged.compareAndSet(false, true)) {
            log.info(
                    "[FIRST_USER_AUDIO] guildId={}, userId={}, userName={}, frameBytes={}",
                    guildId,
                    userId,
                    userName,
                    frameBytes);
        }

        // 화자별 누적 카운터를 업데이트
        SpeakerStats stats = statsByUserId.computeIfAbsent(userId, ignored -> new SpeakerStats(userName));
        stats.updateUserNameIfPlaceholder(userName);
        long packetCount = stats.packetCount.incrementAndGet();
        long totalUserPackets = userPacketCount.incrementAndGet();
        stats.byteCount.addAndGet(frameBytes);
        stats.lastSeenAtMs.set(System.currentTimeMillis());

        // 일정 주기마다만 요약 로그 출력
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
                    + ", firstDecodeFailureReason="
                    + (decodeFailureReason == null ? "-" : decodeFailureReason)
                    + ")";
        }

        // 패킷 수가 많은 화자 순으로 정렬해 보여준다.
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

    private static class SpeakerStats {
        // 표시용 사용자명(첫 수신 시점 기준)
        private volatile String userName;
        private final AtomicLong encodedPacketCount = new AtomicLong();
        private final AtomicLong encodedByteCount = new AtomicLong();
        private final AtomicLong decodedPcmPacketCount = new AtomicLong();
        private final AtomicLong decodedPcmByteCount = new AtomicLong();
        private final AtomicLong packetCount = new AtomicLong();
        private final AtomicLong byteCount = new AtomicLong();
        // 마지막 프레임 수신 시각(ms)
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
