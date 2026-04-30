package com.flodiback.bot.command;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.bot.audio.PerUserAudioReceiveHandler;
import com.flodiback.domain.speech.stt.SttProvider;
import com.flodiback.domain.speech.stt.provider.openai.OpenAiSttProvider;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * Discord 텍스트 명령 처리 리스너.
 *
 * <p>현재 지원 명령:
 * - !ping
 * - !join
 * - !leave
 * - !stats
 */
public class DiscordCommandListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(DiscordCommandListener.class);

    // 명령어 접두사(예: !)
    private final String prefix;
    // 실제 STT 엔진 구현체(현재 OpenAI)
    private final SttProvider sttProvider;
    // 기본 meetingId (현재는 단일 값 사용)
    private final long defaultMeetingId;
    // 길드별 오디오 수신 핸들러 캐시
    private final Map<Long, PerUserAudioReceiveHandler> receiveHandlers = new ConcurrentHashMap<>();

    public DiscordCommandListener() {
        this("!", new OpenAiSttProvider(), 1L);
    }

    public DiscordCommandListener(String prefix, SttProvider sttProvider, long defaultMeetingId) {
        this.prefix = (prefix == null || prefix.isBlank()) ? "!" : prefix.trim();
        this.sttProvider = Objects.requireNonNull(sttProvider);
        this.defaultMeetingId = defaultMeetingId;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 봇 메시지/DM은 명령 처리 대상에서 제외
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        String raw = event.getMessage().getContentRaw().trim();
        if (!raw.startsWith(prefix)) {
            return;
        }

        String command = raw.substring(prefix.length()).trim().toLowerCase();
        switch (command) {
            case "ping" -> event.getChannel().sendMessage("pong").queue();
            case "join" -> handleJoin(event);
            case "leave" -> handleLeave(event);
            case "stats" -> handleStats(event);
            default -> {
                // 미지원 명령은 조용히 무시
            }
        }
    }

    private void handleJoin(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null
                || member.getVoiceState() == null
                || member.getVoiceState().getChannel() == null) {
            event.getChannel().sendMessage("먼저 음성 채널에 들어가줘.").queue();
            return;
        }

        AudioChannel targetChannel = member.getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();

        // 길드별 핸들러를 1개 유지한다.
        // (디스코드 특성상 봇 1개는 길드 내 음성 채널 1개 연결만 가능)
        PerUserAudioReceiveHandler handler = receiveHandlers.computeIfAbsent(
                event.getGuild().getIdLong(),
                guildId -> new PerUserAudioReceiveHandler(guildId, sttProvider, defaultMeetingId));

        // 오디오 수신 핸들러 연결 + 음성 연결
        audioManager.setReceivingHandler(handler);
        audioManager.setAutoReconnect(true);
        audioManager.setSelfMuted(false);
        audioManager.setSelfDeafened(false);
        audioManager.openAudioConnection(targetChannel);

        event.getChannel()
                .sendMessage("입장 완료: "
                        + targetChannel.getName()
                        + " | status="
                        + audioManager.getConnectionStatus()
                        + ", selfMuted="
                        + audioManager.isSelfMuted()
                        + ", selfDeafened="
                        + audioManager.isSelfDeafened())
                .queue();

        log.info(
                "Joined voice channel. guildId={}, channelId={}, channelName={}, meetingId={}",
                event.getGuild().getId(),
                targetChannel.getId(),
                targetChannel.getName(),
                defaultMeetingId);
    }

    private void handleLeave(MessageReceivedEvent event) {
        AudioManager audioManager = event.getGuild().getAudioManager();
        PerUserAudioReceiveHandler handler =
                receiveHandlers.remove(event.getGuild().getIdLong());

        // leave 시점에는 열린 STT 세션을 먼저 commit/end로 닫는다.
        if (handler != null) {
            handler.closeAllSttSessions();
        }

        // 디스코드 음성 연결 종료 + 핸들러 해제
        audioManager.closeAudioConnection();
        audioManager.setReceivingHandler(null);

        event.getChannel().sendMessage("퇴장 완료").queue();
        log.info("Left voice channel. guildId={}", event.getGuild().getId());
    }

    private void handleStats(MessageReceivedEvent event) {
        AudioManager audioManager = event.getGuild().getAudioManager();
        PerUserAudioReceiveHandler handler =
                receiveHandlers.get(event.getGuild().getIdLong());
        if (handler == null) {
            event.getChannel().sendMessage("현재 수신 중인 음성 세션이 없어. 먼저 !join 해줘.").queue();
            return;
        }

        String connectionSummary = "연결 상태: connected="
                + audioManager.isConnected()
                + ", status="
                + audioManager.getConnectionStatus()
                + ", selfMuted="
                + audioManager.isSelfMuted()
                + ", selfDeafened="
                + audioManager.isSelfDeafened();

        event.getChannel()
                .sendMessage(connectionSummary + "\n" + handler.getStatsSummary())
                .queue();
    }
}
