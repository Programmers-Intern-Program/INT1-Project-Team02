package com.flodiback.bot.command;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flodiback.bot.BotEnv;
import com.flodiback.bot.audio.PerUserAudioReceiveHandler;
import com.flodiback.domain.speech.stt.SttProvider;
import com.flodiback.domain.speech.stt.provider.openai.OpenAiSttProvider;

import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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

    private enum ProjectCreationStep {
        WAITING_NAME,
        WAITING_DESCRIPTION,
        WAITING_TECH_STACK
    }

    private static final long PENDING_TTL_MS = 60_000;

    private record ProjectCreationState(ProjectCreationStep step, String name, String description, long createdAt) {}

    private record MeetingConfirmationState(long createdAt) {}

    // 명령어 접두사(예: !)
    private final String prefix;
    // 실제 STT 엔진 구현체(현재 OpenAI)
    private final SttProvider sttProvider;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String internalBaseUrl;
    private final String internalApiKey;
    // 길드별 오디오 수신 핸들러 캐시
    private final Map<Long, PerUserAudioReceiveHandler> receiveHandlers = new ConcurrentHashMap<>();
    // 길드별 활성 meetingId
    private final Map<Long, Long> activeMeetingIdByGuild = new ConcurrentHashMap<>();
    // 채널별 진행 중인 프로젝트 생성 상태
    private final Map<Long, ProjectCreationState> pendingProjectCreations = new ConcurrentHashMap<>();
    // 채널별 회의 시작 확인 대기 상태
    private final Map<Long, MeetingConfirmationState> pendingMeetingConfirmations = new ConcurrentHashMap<>();

    public DiscordCommandListener() {
        this("!", new OpenAiSttProvider(), 1L);
    }

    public DiscordCommandListener(String prefix, SttProvider sttProvider, long defaultMeetingId) {
        this.prefix = (prefix == null || prefix.isBlank()) ? "!" : prefix.trim();
        this.sttProvider = Objects.requireNonNull(sttProvider);
        this.internalBaseUrl = normalizeBaseUrl(BotEnv.getOrDefault("INTERNAL_API_BASE_URL", "http://localhost:8080"));
        this.internalApiKey = BotEnv.get("INTERNAL_API_KEY");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 봇 메시지/DM은 명령 처리 대상에서 제외
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        long channelId = event.getChannel().getIdLong();

        // 회의 시작 확인 대기 중인 채널이면 먼저 처리
        if (pendingMeetingConfirmations.containsKey(channelId)) {
            handleMeetingConfirmationStep(event, channelId);
            return;
        }

        // 프로젝트 생성 대화 중인 채널이면 명령어보다 먼저 처리
        if (pendingProjectCreations.containsKey(channelId)) {
            handleProjectCreationStep(event, channelId);
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
            case "project start" -> handleProjectStart(event, channelId);
            case "meeting start" -> new Thread(() -> handleMeetingStart(event, channelId)).start();
            case "meeting end" -> handleMeetingEnd(event);
            default -> {
                // 미지원 명령은 조용히 무시
            }
        }
    }

    private void handleProjectStart(MessageReceivedEvent event, long channelId) {
        pendingProjectCreations.put(
                channelId,
                new ProjectCreationState(ProjectCreationStep.WAITING_NAME, null, null, System.currentTimeMillis()));
        event.getChannel().sendMessage("프로젝트 이름을 입력해주세요.").queue();
    }

    private void handleProjectCreationStep(MessageReceivedEvent event, long channelId) {
        ProjectCreationState state = pendingProjectCreations.get(channelId);

        if (System.currentTimeMillis() - state.createdAt() > PENDING_TTL_MS) {
            pendingProjectCreations.remove(channelId);
            event.getChannel()
                    .sendMessage("⏰ 프로젝트 생성 시간이 초과됐습니다. 다시 `!project start`를 입력해주세요.")
                    .queue();
            return;
        }

        String input = event.getMessage().getContentRaw().trim();

        switch (state.step()) {
            case WAITING_NAME -> {
                if (input.isBlank()) {
                    event.getChannel()
                            .sendMessage("프로젝트 이름은 필수입니다. 이름을 입력해주세요.")
                            .queue();
                    return;
                }
                pendingProjectCreations.put(
                        channelId,
                        new ProjectCreationState(
                                ProjectCreationStep.WAITING_DESCRIPTION, input, null, state.createdAt()));
                event.getChannel().sendMessage("프로젝트 설명을 입력해주세요. (건너뛰려면 '.')").queue();
            }
            case WAITING_DESCRIPTION -> {
                String description = ".".equals(input) ? null : input;
                pendingProjectCreations.put(
                        channelId,
                        new ProjectCreationState(
                                ProjectCreationStep.WAITING_TECH_STACK, state.name(), description, state.createdAt()));
                event.getChannel().sendMessage("기술 스택을 입력해주세요. (건너뛰려면 '.')").queue();
            }
            case WAITING_TECH_STACK -> {
                String techStack = ".".equals(input) ? null : input;
                pendingProjectCreations.remove(channelId);
                Long projectId = createProject(
                        event.getChannel().getIdLong(),
                        event.getGuild().getIdLong(),
                        state.name(),
                        state.description(),
                        techStack,
                        event);
                if (projectId != null) {
                    event.getChannel()
                            .sendMessage("✅ 프로젝트 [" + state.name() + "]가 생성됐습니다! (id=" + projectId + ")")
                            .queue();
                }
            }
        }
    }

    private void handleMeetingStart(MessageReceivedEvent event, long channelId) {
        Member member = event.getMember();
        if (member == null
                || member.getVoiceState() == null
                || member.getVoiceState().getChannel() == null) {
            event.getChannel().sendMessage("먼저 음성 채널에 들어가줘.").queue();
            return;
        }

        Long projectId = fetchProjectIdByChannel(channelId);
        if (projectId != null) {
            startMeeting(event, projectId);
            return;
        }

        // 채널에 연결된 프로젝트가 없으면 확인 요청
        pendingMeetingConfirmations.put(channelId, new MeetingConfirmationState(System.currentTimeMillis()));
        event.getChannel()
                .sendMessage("⚠️ 이 채널에 연결된 프로젝트가 없어서 회의가 저장되지 않아요.\n" + "그래도 진행하시겠어요? (yes / no)")
                .queue();
    }

    private void handleMeetingConfirmationStep(MessageReceivedEvent event, long channelId) {
        MeetingConfirmationState state = pendingMeetingConfirmations.get(channelId);

        if (System.currentTimeMillis() - state.createdAt() > PENDING_TTL_MS) {
            pendingMeetingConfirmations.remove(channelId);
            event.getChannel()
                    .sendMessage("⏰ 응답 시간이 초과됐습니다. 다시 `!meeting start`를 입력해주세요.")
                    .queue();
            return;
        }

        String input = event.getMessage().getContentRaw().trim().toLowerCase();
        pendingMeetingConfirmations.remove(channelId);

        if ("yes".equals(input)) {
            startMeeting(event, null);
        } else if ("no".equals(input)) {
            event.getChannel().sendMessage("회의 시작을 취소했습니다.").queue();
        } else {
            event.getChannel()
                    .sendMessage("'yes' 또는 'no'로 답해주세요. 다시 `!meeting start`를 입력해주세요.")
                    .queue();
        }
    }

    private void handleMeetingEnd(MessageReceivedEvent event) {
        long guildId = event.getGuild().getIdLong();
        AudioManager audioManager = event.getGuild().getAudioManager();
        PerUserAudioReceiveHandler handler = receiveHandlers.remove(guildId);

        if (handler != null) {
            handler.closeAllSttSessions();
        }

        audioManager.closeAudioConnection();
        audioManager.setReceivingHandler(null);
        audioManager.setConnectionListener(null);

        Long meetingId = activeMeetingIdByGuild.remove(guildId);
        if (meetingId == null) {
            event.getChannel().sendMessage("진행 중인 회의가 없어요.").queue();
            return;
        }

        endMeeting(guildId, meetingId);
        event.getChannel().sendMessage("✅ 회의가 종료됐습니다. 요약을 생성 중이에요...").queue();
        log.info("[미팅/종료명령] guildId={}, meetingId={}", guildId, meetingId);
    }

    private Long fetchProjectIdByChannel(long channelId) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/projects/channel/" + channelId))
                    .GET();
            attachInternalApiKey(requestBuilder);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() / 100 != 2) {
                log.warn("[프로젝트/채널조회실패] channelId={}, status={}", channelId, response.statusCode());
                return null;
            }

            JsonNode idNode =
                    objectMapper.readTree(response.body()).path("data").path("id");
            return idNode.isNumber() ? idNode.asLong() : null;
        } catch (Exception e) {
            log.warn("[프로젝트/채널조회예외] channelId={}", channelId, e);
            return null;
        }
    }

    private void startMeeting(MessageReceivedEvent event, Long projectId) {
        Member member = event.getMember();
        AudioChannel targetChannel = member.getVoiceState().getChannel();
        long guildId = event.getGuild().getIdLong();

        Long meetingId = createMeeting(guildId, event.getGuild().getName(), projectId);
        if (meetingId == null) {
            event.getChannel().sendMessage("❌ 회의 생성 실패. 서버 로그를 확인해줘.").queue();
            return;
        }

        AudioManager audioManager = event.getGuild().getAudioManager();
        PerUserAudioReceiveHandler existing = receiveHandlers.remove(guildId);
        if (existing != null) {
            existing.closeAllSttSessions();
        }

        PerUserAudioReceiveHandler handler =
                new PerUserAudioReceiveHandler(guildId, event.getGuild(), sttProvider, meetingId);
        receiveHandlers.put(guildId, handler);
        activeMeetingIdByGuild.put(guildId, meetingId);
        handler.updateCaptionChannel(event.getChannel());

        audioManager.setReceivingHandler(handler);
        audioManager.setConnectionListener(new LoggingConnectionListener(guildId, targetChannel));
        audioManager.setAutoReconnect(true);
        audioManager.setSelfMuted(false);
        audioManager.setSelfDeafened(false);
        audioManager.openAudioConnection(targetChannel);

        event.getChannel()
                .sendMessage("🎙️ 회의 시작! 음성 채널 [" + targetChannel.getName() + "]에 입장했어요. (meetingId=" + meetingId + ")")
                .queue();
        log.info(
                "[미팅/시작] guildId={}, meetingId={}, projectId={}, channel={}",
                guildId,
                meetingId,
                projectId,
                targetChannel.getName());
    }

    private Long createProject(
            long channelId,
            long guildId,
            String name,
            String description,
            String techStack,
            MessageReceivedEvent event) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", name);
            if (description != null) body.put("description", description);
            else body.putNull("description");
            if (techStack != null) body.put("techStack", techStack);
            else body.putNull("techStack");
            body.putNull("serverId");
            body.put("channelId", String.valueOf(channelId));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/projects"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            attachInternalApiKey(requestBuilder);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.warn(
                        "[프로젝트/생성실패] channelId={}, status={}, body={}",
                        channelId,
                        response.statusCode(),
                        response.body());
                event.getChannel()
                        .sendMessage("❌ 프로젝트 생성에 실패했습니다. 서버 로그를 확인해주세요.")
                        .queue();
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode idNode = root.path("data").path("id");
            if (!idNode.isNumber()) {
                log.warn("[프로젝트/생성응답오류] channelId={}, body={}", channelId, response.body());
                event.getChannel().sendMessage("❌ 프로젝트 생성 응답 오류입니다.").queue();
                return null;
            }

            long projectId = idNode.asLong();
            log.info("[프로젝트/생성성공] channelId={}, projectId={}", channelId, projectId);
            return projectId;
        } catch (Exception e) {
            log.warn("[프로젝트/생성예외] channelId={}", channelId, e);
            event.getChannel().sendMessage("❌ 프로젝트 생성 중 오류가 발생했습니다.").queue();
            return null;
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

        long guildId = event.getGuild().getIdLong();
        Long meetingId = createMeeting(guildId, event.getGuild().getName(), null);
        if (meetingId == null) {
            event.getChannel().sendMessage("회의 생성 실패로 입장을 중단했어. 서버 로그를 확인해줘.").queue();
            return;
        }

        AudioChannel targetChannel = member.getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();

        PerUserAudioReceiveHandler existing = receiveHandlers.remove(guildId);
        if (existing != null) {
            existing.closeAllSttSessions();
        }

        // 길드별 핸들러를 1개 유지한다.
        // (디스코드 특성상 봇 1개는 길드 내 음성 채널 1개 연결만 가능)
        PerUserAudioReceiveHandler handler =
                new PerUserAudioReceiveHandler(guildId, event.getGuild(), sttProvider, meetingId);
        receiveHandlers.put(guildId, handler);
        activeMeetingIdByGuild.put(guildId, meetingId);
        handler.updateCaptionChannel(event.getChannel());

        // 오디오 수신 핸들러 연결 + 음성 연결
        audioManager.setReceivingHandler(handler);
        audioManager.setConnectionListener(
                new LoggingConnectionListener(event.getGuild().getIdLong(), targetChannel));
        audioManager.setAutoReconnect(true);
        audioManager.setSelfMuted(false);
        audioManager.setSelfDeafened(false);
        audioManager.openAudioConnection(targetChannel);

        event.getChannel()
                .sendMessage("입장 요청 완료: "
                        + targetChannel.getName()
                        + " | 현재상태="
                        + audioManager.getConnectionStatus()
                        + " (오디오 연결은 비동기로 진행됨)"
                        + ", connected="
                        + audioManager.isConnected()
                        + ", selfMuted="
                        + audioManager.isSelfMuted()
                        + ", selfDeafened="
                        + audioManager.isSelfDeafened())
                .queue();

        log.info(
                "[디스코드/입장] guildId={}, channelId={}, channelName={}, meetingId={}, voiceMemberCount={}, voiceMembers={}",
                event.getGuild().getId(),
                targetChannel.getId(),
                targetChannel.getName(),
                meetingId,
                targetChannel.getMembers().size(),
                summarizeVoiceMembers(targetChannel));
    }

    private void handleLeave(MessageReceivedEvent event) {
        long guildId = event.getGuild().getIdLong();
        AudioManager audioManager = event.getGuild().getAudioManager();
        PerUserAudioReceiveHandler handler = receiveHandlers.remove(guildId);

        // leave 시점에는 열린 STT 세션을 먼저 commit/end로 닫는다.
        if (handler != null) {
            handler.closeAllSttSessions();
        }

        // 디스코드 음성 연결 종료 + 핸들러 해제
        audioManager.closeAudioConnection();
        audioManager.setReceivingHandler(null);
        audioManager.setConnectionListener(null);

        Long meetingId = activeMeetingIdByGuild.remove(guildId);
        if (meetingId != null) {
            endMeeting(guildId, meetingId);
        }

        event.getChannel().sendMessage("퇴장 완료").queue();
        log.info("[디스코드/퇴장] guildId={}, meetingId={}", event.getGuild().getId(), meetingId);
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

    private String summarizeVoiceMembers(AudioChannel channel) {
        if (channel.getMembers().isEmpty()) {
            return "-";
        }
        return channel.getMembers().stream()
                .map(member -> member.getId() + ":" + member.getUser().getName())
                .limit(10)
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    private Long createMeeting(long guildId, String guildName, Long projectId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            if (projectId != null) body.put("projectId", projectId);
            else body.putNull("projectId");
            body.put("title", "Discord " + guildName + " " + LocalDateTime.now());

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/meetings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            attachInternalApiKey(requestBuilder);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[미팅/생성실패] guildId={}, status={}, body={}", guildId, response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode idNode = root.path("data").path("id");
            if (!idNode.isNumber()) {
                log.warn("[미팅/생성응답오류] guildId={}, body={}", guildId, response.body());
                return null;
            }

            long meetingId = idNode.asLong();
            log.info("[미팅/생성성공] guildId={}, meetingId={}", guildId, meetingId);
            return meetingId;
        } catch (Exception exception) {
            log.warn("[미팅/생성예외] guildId={}", guildId, exception);
            return null;
        }
    }

    private void endMeeting(long guildId, long meetingId) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/meetings/" + meetingId + "/end"))
                    .PUT(HttpRequest.BodyPublishers.noBody());
            attachInternalApiKey(requestBuilder);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn(
                        "[미팅/종료실패] guildId={}, meetingId={}, status={}, body={}",
                        guildId,
                        meetingId,
                        response.statusCode(),
                        response.body());
                return;
            }

            log.info("[미팅/종료성공] guildId={}, meetingId={}", guildId, meetingId);
        } catch (Exception exception) {
            log.warn("[미팅/종료예외] guildId={}, meetingId={}", guildId, meetingId, exception);
        }
    }

    private void attachInternalApiKey(HttpRequest.Builder requestBuilder) {
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            requestBuilder.header("X-Internal-Api-Key", internalApiKey);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "http://localhost:8080" : baseUrl.trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private final class LoggingConnectionListener implements ConnectionListener {
        private final long guildId;
        private final String channelId;
        private final String channelName;

        private LoggingConnectionListener(long guildId, AudioChannel channel) {
            this.guildId = guildId;
            this.channelId = channel.getId();
            this.channelName = channel.getName();
        }

        @Override
        public void onStatusChange(ConnectionStatus status) {
            log.info(
                    "[디스코드/오디오상태] guildId={}, channelId={}, channelName={}, status={}",
                    guildId,
                    channelId,
                    channelName,
                    status);
        }

        @Override
        public void onUserSpeakingModeUpdate(
                User user, java.util.EnumSet<net.dv8tion.jda.api.audio.SpeakingMode> modes) {
            log.debug(
                    "[디스코드/말하기상태] guildId={}, channelId={}, userId={}, userName={}, modes={}",
                    guildId,
                    channelId,
                    user.getId(),
                    user.getName(),
                    modes);
        }
    }
}
