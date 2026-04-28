package com.flodiback.bot.command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.bot.audio.PerUserAudioReceiveHandler;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * Discord 텍스트 명령 처리 리스너.
 *
 * <p>현재 지원 명령: !ping, !join, !leave, !stats
 */
public class DiscordCommandListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(DiscordCommandListener.class);

    // 명령어 접두사 (기본값: !)
    private final String prefix;
    // 길드별 오디오 수신 핸들러를 보관한다.
    private final Map<Long, PerUserAudioReceiveHandler> receiveHandlers = new ConcurrentHashMap<>();

    public DiscordCommandListener() {
        this("!");
    }

    public DiscordCommandListener(String prefix) {
        this.prefix = (prefix == null || prefix.isBlank()) ? "!" : prefix.trim();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 봇 메시지/DM은 처리하지 않는다.
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        String raw = event.getMessage().getContentRaw().trim();
        // 접두사로 시작하지 않으면 명령으로 보지 않는다.
        if (!raw.startsWith(prefix)) {
            return;
        }

        // 접두사 제거 후 명령 텍스트만 추출
        String command = raw.substring(prefix.length()).trim().toLowerCase();
        switch (command) {
            case "ping" -> event.getChannel().sendMessage("pong").queue();
            case "join" -> handleJoin(event);
            case "leave" -> handleLeave(event);
            case "stats" -> handleStats(event);
            default -> {
                // 현재는 알 수 없는 명령어를 무시한다.
            }
        }
    }

    private void handleJoin(MessageReceivedEvent event) {
        Member member = event.getMember();
        // 명령 실행자가 음성 채널에 없으면 입장할 채널을 결정할 수 없다.
        if (member == null
                || member.getVoiceState() == null
                || member.getVoiceState().getChannel() == null) {
            event.getChannel().sendMessage("먼저 음성 채널에 들어가줘.").queue();
            return;
        }

        // 실행자와 같은 음성 채널로 봇을 입장시킨다.
        AudioChannel targetChannel = member.getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();
        // 길드별 수신 핸들러를 한 번만 만들고 재사용한다.
        PerUserAudioReceiveHandler handler = receiveHandlers.computeIfAbsent(
                event.getGuild().getIdLong(),
                guildId -> new PerUserAudioReceiveHandler(event.getGuild().getIdLong()));

        // 수신 핸들러 연결 + self deafen 해제(수신 필요) + 실제 연결
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
                "Joined voice channel. guildId={}, channelId={}, channelName={}",
                event.getGuild().getId(),
                targetChannel.getId(),
                targetChannel.getName());
    }

    private void handleLeave(MessageReceivedEvent event) {
        AudioManager audioManager = event.getGuild().getAudioManager();
        // 연결 해제 + 핸들러 정리
        audioManager.closeAudioConnection();
        audioManager.setReceivingHandler(null);
        receiveHandlers.remove(event.getGuild().getIdLong());
        event.getChannel().sendMessage("퇴장 완료").queue();
        log.info("Left voice channel. guildId={}", event.getGuild().getId());
    }

    private void handleStats(MessageReceivedEvent event) {
        // 현재 길드 세션의 누적 통계를 텍스트로 응답한다.
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
