package com.flodiback.bot.command;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flodiback.bot.BotEnv;
import com.flodiback.bot.audio.PerUserAudioReceiveHandler;
import com.flodiback.domain.speech.stt.SttProvider;
import com.flodiback.domain.speech.stt.provider.openai.OpenAiSttProvider;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.modals.Modal;

/**
 * Discord 텍스트 명령과 컴포넌트 인터랙션을 처리하는 리스너.
 *
 * <p>현재 지원 명령:
 * - !ping
 * - !join
 * - !leave
 * - !stats
 */
public class DiscordCommandListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(DiscordCommandListener.class);
    private static final Color FLODI_COLOR = new Color(88, 166, 255);
    private static final String PANEL_COMMAND = "flodi";
    private static final String BUTTON_PANEL_OPEN = "flodi:panel:open";
    private static final String BUTTON_DASHBOARD_VIEW = "flodi:dashboard:view";
    private static final String BUTTON_PROJECT_CREATE = "flodi:project:create";
    private static final String BUTTON_MEETING_START = "flodi:meeting:start";
    private static final String BUTTON_MEETING_END = "flodi:meeting:end";
    private static final String BUTTON_MEETING_START_WITHOUT_PROJECT = "flodi:meeting:start-without-project";
    private static final String BUTTON_MEETING_CANCEL = "flodi:meeting:cancel";
    private static final String SELECT_PROJECT = "flodi:project:select";
    private static final String MODAL_PROJECT_CREATE = "flodi:project:create-modal";
    private static final String MODAL_PROJECT_NAME = "flodi:project:name";
    private static final String MODAL_PROJECT_DESCRIPTION = "flodi:project:description";
    private static final String MODAL_PROJECT_TECH_STACK = "flodi:project:tech-stack";
    private static final int PROJECT_SELECT_LIMIT = 25;
    private static final int DECISION_PREVIEW_LIMIT = 3;
    private static final int TEMPORARY_MESSAGE_SECONDS = 45;
    private static final String ENV_ALLOWED_GUILD_IDS = "DISCORD_ALLOWED_GUILD_IDS";

    private enum ProjectCreationStep {
        WAITING_NAME,
        WAITING_DESCRIPTION,
        WAITING_TECH_STACK
    }

    private static final long PENDING_TTL_MS = 60_000;

    private record ProjectCreationState(ProjectCreationStep step, String name, String description, long createdAt) {}

    private record MeetingStartContext(
            long guildId,
            String guildName,
            AudioChannel voiceChannel,
            MessageChannel textChannel,
            AudioManager audioManager,
            Guild guild,
            long channelId) {}

    private record MeetingConfirmationState(MeetingStartContext ctx, long createdAt) {}

    private record MeetingEndResult(Long meetingId, boolean hadVoiceState) {}

    private record ProjectEditState(
            ProjectCreationStep step, String name, String description, long projectId, long createdAt) {}

    private record ProjectDeletionState(long projectId, long createdAt) {}

    private record MeetingDeletionState(long meetingId, long createdAt) {}

    // 명령어 접두사. 기본값은 !.
    private final String prefix;
    // 실제 STT 엔진 구현체. 현재 기본값은 OpenAI.
    private final SttProvider sttProvider;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String internalBaseUrl;
    private final String internalApiKey;
    private final Set<Long> allowedGuildIds;
    // 길드별 오디오 수신 핸들러 캐시
    private final Map<Long, PerUserAudioReceiveHandler> receiveHandlers = new ConcurrentHashMap<>();
    // 길드별 활성 meetingId
    private final Map<Long, Long> activeMeetingIdByGuild = new ConcurrentHashMap<>();
    // 길드별 회의 생성 중복 요청 방지
    private final Set<Long> startingGuildIds = ConcurrentHashMap.newKeySet();
    // 채널별 진행 중인 프로젝트 생성 상태
    private final Map<Long, ProjectCreationState> pendingProjectCreations = new ConcurrentHashMap<>();
    // 채널별 회의 시작 확인 대기 상태
    private final Map<Long, MeetingConfirmationState> pendingMeetingConfirmations = new ConcurrentHashMap<>();
    // 채널별 프로젝트 수정 상태
    private final Map<Long, ProjectEditState> pendingProjectEdits = new ConcurrentHashMap<>();
    // 채널별 프로젝트 삭제 확인 대기 상태
    private final Map<Long, ProjectDeletionState> pendingProjectDeletions = new ConcurrentHashMap<>();
    // 채널별 회의 삭제 확인 대기 상태
    private final Map<Long, MeetingDeletionState> pendingMeetingDeletions = new ConcurrentHashMap<>();

    public DiscordCommandListener() {
        this("!", new OpenAiSttProvider(), 1L);
    }

    public DiscordCommandListener(String prefix, SttProvider sttProvider, long defaultMeetingId) {
        this.prefix = (prefix == null || prefix.isBlank()) ? "!" : prefix.trim();
        this.sttProvider = Objects.requireNonNull(sttProvider);
        this.internalBaseUrl = normalizeBaseUrl(BotEnv.getOrDefault("INTERNAL_API_BASE_URL", "http://localhost:8080"));
        this.internalApiKey = BotEnv.get("INTERNAL_API_KEY");
        this.allowedGuildIds = parseAllowedGuildIds(BotEnv.get(ENV_ALLOWED_GUILD_IDS));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // 봇 메시지와 DM은 명령 처리 대상에서 제외
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }
        if (!isAllowedGuild(event.getGuild().getIdLong())) {
            log.debug(
                    "[봇/서버무시] guildId={}, reason=not_allowed", event.getGuild().getIdLong());
            return;
        }

        long channelId = event.getChannel().getIdLong();

        // 회의 시작 확인 대기 중인 채널이면 먼저 처리
        if (pendingMeetingConfirmations.containsKey(channelId)) {
            handleMeetingConfirmationStep(event, channelId);
            return;
        }

        // 프로젝트 생성 대화 중인 채널이면 명령보다 먼저 처리
        if (pendingProjectCreations.containsKey(channelId)) {
            handleProjectCreationStep(event, channelId);
            return;
        }

        // 프로젝트 수정 대화 중인 채널이면 먼저 처리
        if (pendingProjectEdits.containsKey(channelId)) {
            new Thread(() -> handleProjectEditStep(event, channelId)).start();
            return;
        }

        // 프로젝트 삭제 확인 대기 중인 채널이면 먼저 처리
        if (pendingProjectDeletions.containsKey(channelId)) {
            new Thread(() -> handleProjectDeletionStep(event, channelId)).start();
            return;
        }

        // 회의 삭제 확인 대기 중인 채널이면 먼저 처리
        if (pendingMeetingDeletions.containsKey(channelId)) {
            new Thread(() -> handleMeetingDeletionStep(event, channelId)).start();
            return;
        }

        String raw = event.getMessage().getContentRaw().trim();
        if (!raw.startsWith(prefix)) {
            return;
        }

        String command = raw.substring(prefix.length()).trim().toLowerCase();
        switch (command) {
            case "ping" -> event.getChannel().sendMessage("pong").queue();
            case PANEL_COMMAND -> sendPanelLauncher(event.getChannel());
            case "join" -> handleJoin(event);
            case "leave" -> handleLeave(event);
            case "stats" -> handleStats(event);
            case "project start" -> handleProjectStart(event, channelId);
            case "meeting start" -> {
                Member member = event.getMember();
                if (member == null
                        || member.getVoiceState() == null
                        || member.getVoiceState().getChannel() == null) {
                    event.getChannel().sendMessage("먼저 음성 채널에 들어가줘.").queue();
                    return;
                }
                MeetingStartContext ctx = new MeetingStartContext(
                        event.getGuild().getIdLong(),
                        event.getGuild().getName(),
                        member.getVoiceState().getChannel(),
                        event.getChannel(),
                        event.getGuild().getAudioManager(),
                        event.getGuild(),
                        channelId);
                new Thread(() -> handleMeetingStart(ctx)).start();
            }
            case "meeting list" -> new Thread(() -> handleMeetingList(event, channelId)).start();
            case "meeting end" -> handleMeetingEnd(event);
            default -> {
                if (command.startsWith("project start ")) {
                    String arg = command.substring("project start ".length()).trim();
                    new Thread(() -> handleProjectStartWithArg(event, channelId, arg)).start();
                } else if (command.startsWith("meeting delete ")) {
                    String arg = command.substring("meeting delete ".length()).trim();
                    new Thread(() -> handleMeetingDelete(event, arg)).start();
                }
                // 그 외 알 수 없는 명령은 조용히 무시
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("서버 채널에서만 사용할 수 있어요.").setEphemeral(true).queue();
            return;
        }
        if (!isAllowedGuild(event.getGuild().getIdLong())) {
            log.debug(
                    "[봇/인터랙션무시] guildId={}, componentId={}, reason=not_allowed",
                    event.getGuild().getIdLong(),
                    event.getComponentId());
            return;
        }

        switch (event.getComponentId()) {
            case BUTTON_PANEL_OPEN -> {
                event.replyEmbeds(buildFlodiPanelEmbed().build())
                        .setComponents(buildFlodiPanelComponents())
                        .setEphemeral(true)
                        .queue();
            }
            case BUTTON_DASHBOARD_VIEW -> {
                event.deferReply(true).queue();
                showDashboard(event);
            }
            case BUTTON_PROJECT_CREATE ->
                event.replyModal(buildProjectCreateModal()).queue();
            case BUTTON_MEETING_START -> handleMeetingStartButton(event);
            case BUTTON_MEETING_START_WITHOUT_PROJECT -> handleMeetingStartWithoutProjectButton(event);
            case BUTTON_MEETING_CANCEL -> {
                pendingMeetingConfirmations.remove(event.getChannel().getIdLong());
                event.reply("회의 시작을 취소했습니다.").setEphemeral(true).queue();
            }
            case BUTTON_MEETING_END -> {
                event.deferReply(true).queue();
                MeetingEndResult result = endActiveMeeting(event.getGuild());
                event.getHook()
                        .sendMessage(buildMeetingEndMessage(result))
                        .setEphemeral(true)
                        .queue();
            }
            default -> {
                // 다른 컴포넌트 인터랙션은 이 리스너에서 처리하지 않는다.
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!SELECT_PROJECT.equals(event.getComponentId())) {
            return;
        }
        if (!event.isFromGuild() || !isAllowedGuild(event.getGuild().getIdLong())) {
            return;
        }
        event.deferReply(true).queue();
        String selected = event.getValues().isEmpty() ? null : event.getValues().getFirst();
        if (selected == null) {
            event.getHook().sendMessage("선택된 프로젝트가 없어요.").setEphemeral(true).queue();
            return;
        }

        try {
            long projectId = Long.parseLong(selected);
            JsonNode project = fetchData("/api/v1/projects/" + projectId, "프로젝트 상세 조회");
            if (project == null) {
                event.getHook()
                        .sendMessage("프로젝트 정보를 가져오지 못했어요.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            sendProjectDashboard(event, project, "선택한 프로젝트 대시보드예요.");
        } catch (NumberFormatException exception) {
            event.getHook()
                    .sendMessage("프로젝트 선택값이 올바르지 않아요.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!MODAL_PROJECT_CREATE.equals(event.getModalId())) {
            return;
        }
        if (!event.isFromGuild() || !isAllowedGuild(event.getGuild().getIdLong())) {
            return;
        }

        event.deferReply(true).queue();
        JsonNode project = createProjectFromModal(event);
        if (project == null) {
            event.getHook()
                    .sendMessage("프로젝트 생성에 실패했어요. 입력값이나 서버 로그를 확인해주세요.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        sendProjectDashboard(event, project, "프로젝트를 생성했어요.");
    }

    private void sendPanelLauncher(MessageChannel channel) {
        channel.sendMessage("Flodi 개인 패널을 열어볼게요.")
                .setComponents(ActionRow.of(Button.secondary(BUTTON_PANEL_OPEN, "개인 패널 열기")))
                .queue(message -> queueDelete(message, TEMPORARY_MESSAGE_SECONDS));
    }

    private EmbedBuilder buildFlodiPanelEmbed() {
        return new EmbedBuilder()
                .setColor(FLODI_COLOR)
                .setTitle("Flodi 회의 패널")
                .setDescription("이 패널은 누른 사람에게만 보이는 개인 조작판이에요.")
                .addField("대시보드", "현재 채널의 프로젝트, 회의, 음성 연결 상태를 한 번에 확인해요.", false)
                .addField("회의", "음성 채널에 들어간 뒤 회의를 시작하면 STT 기록이 저장돼요.", false);
    }

    private List<ActionRow> buildFlodiPanelComponents() {
        return List.of(
                ActionRow.of(
                        Button.secondary(BUTTON_DASHBOARD_VIEW, "대시보드"),
                        Button.secondary(BUTTON_PROJECT_CREATE, "프로젝트 생성")),
                ActionRow.of(
                        Button.primary(BUTTON_MEETING_START, "회의 시작"), Button.danger(BUTTON_MEETING_END, "회의 종료")));
    }

    private void handleMeetingStartButton(ButtonInteractionEvent event) {
        Member member = event.getMember();
        if (member == null
                || member.getVoiceState() == null
                || member.getVoiceState().getChannel() == null) {
            event.reply("먼저 음성 채널에 들어가줘.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        long channelId = event.getChannel().getIdLong();
        MeetingStartContext ctx = new MeetingStartContext(
                event.getGuild().getIdLong(),
                event.getGuild().getName(),
                member.getVoiceState().getChannel(),
                event.getChannel(),
                event.getGuild().getAudioManager(),
                event.getGuild(),
                channelId);

        Long projectId = fetchProjectIdByChannel(channelId);
        if (projectId != null) {
            new Thread(() -> startMeeting(ctx, projectId)).start();
            event.getHook().sendMessage("회의 시작 요청을 처리했어요.").setEphemeral(true).queue();
            return;
        }

        pendingMeetingConfirmations.put(channelId, new MeetingConfirmationState(ctx, System.currentTimeMillis()));
        event.getHook()
                .sendMessageEmbeds(new EmbedBuilder()
                        .setColor(Color.ORANGE)
                        .setTitle("연결된 프로젝트가 없어요")
                        .setDescription("이 채널에 연결된 프로젝트가 없어서 회의가 프로젝트에 저장되지 않아요.")
                        .build())
                .setComponents(ActionRow.of(
                        Button.secondary(BUTTON_MEETING_START_WITHOUT_PROJECT, "프로젝트 없이 시작"),
                        Button.secondary(BUTTON_PROJECT_CREATE, "프로젝트 생성"),
                        Button.secondary(BUTTON_MEETING_CANCEL, "취소")))
                .setEphemeral(true)
                .queue();
    }

    private void handleMeetingStartWithoutProjectButton(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        long channelId = event.getChannel().getIdLong();
        MeetingConfirmationState state = pendingMeetingConfirmations.remove(channelId);
        if (state == null || System.currentTimeMillis() - state.createdAt() > PENDING_TTL_MS) {
            event.getHook()
                    .sendMessage("회의 시작 확인이 만료됐어요. 다시 `!flodi`에서 회의 시작을 눌러주세요.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        new Thread(() -> startMeeting(state.ctx(), null)).start();
        event.getHook().sendMessage("프로젝트 없이 회의를 시작할게요.").setEphemeral(true).queue();
    }

    private void showDashboard(ButtonInteractionEvent event) {
        JsonNode connectedProject =
                fetchData("/api/v1/projects/channel/" + event.getChannel().getId(), "채널 프로젝트 조회");
        if (connectedProject != null) {
            sendProjectDashboard(event, connectedProject, "현재 채널 대시보드예요.");
            return;
        }

        JsonNode projects = fetchData("/api/v1/projects", "프로젝트 목록 조회");
        List<ActionRow> components = new ArrayList<>();
        components.add(ActionRow.of(Button.secondary(BUTTON_PROJECT_CREATE, "프로젝트 생성")));
        if (projects != null && projects.isArray() && !projects.isEmpty()) {
            components.add(ActionRow.of(buildProjectSelectMenu(projects)));
        }

        event.getHook()
                .sendMessageEmbeds(buildEmptyDashboardEmbed(event.getGuild()).build())
                .setComponents(components)
                .setEphemeral(true)
                .queue();
    }

    private void sendProjectDashboard(ButtonInteractionEvent event, JsonNode project, String message) {
        long projectId = project.path("id").asLong();
        JsonNode decisions = fetchData("/api/v1/projects/" + projectId + "/decisions", "프로젝트 결정사항 조회");
        Long connectedProjectId = fetchProjectIdByChannel(event.getChannel().getIdLong());
        boolean connected = connectedProjectId != null && connectedProjectId == projectId;
        event.getHook()
                .sendMessage(message)
                .addEmbeds(buildProjectDashboardEmbed(event.getGuild(), project, decisions, connected)
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private void sendProjectDashboard(StringSelectInteractionEvent event, JsonNode project, String message) {
        long projectId = project.path("id").asLong();
        JsonNode decisions = fetchData("/api/v1/projects/" + projectId + "/decisions", "프로젝트 결정사항 조회");
        Long connectedProjectId = fetchProjectIdByChannel(event.getChannel().getIdLong());
        boolean connected = connectedProjectId != null && connectedProjectId == projectId;
        event.getHook()
                .sendMessage(message)
                .addEmbeds(buildProjectDashboardEmbed(event.getGuild(), project, decisions, connected)
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private void sendProjectDashboard(ModalInteractionEvent event, JsonNode project, String message) {
        long projectId = project.path("id").asLong();
        JsonNode decisions = fetchData("/api/v1/projects/" + projectId + "/decisions", "프로젝트 결정사항 조회");
        event.getHook()
                .sendMessage(message)
                .addEmbeds(buildProjectDashboardEmbed(event.getGuild(), project, decisions, true)
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private EmbedBuilder buildEmptyDashboardEmbed(Guild guild) {
        Long activeMeetingId = activeMeetingIdByGuild.get(guild.getIdLong());
        return new EmbedBuilder()
                .setColor(FLODI_COLOR)
                .setTitle("Flodi 대시보드")
                .setDescription("이 채널에 연결된 프로젝트가 없어요.")
                .addField("프로젝트", "프로젝트를 생성하거나 기존 프로젝트를 선택할 수 있어요.", false)
                .addField("활성 회의", activeMeetingId != null ? "meetingId=" + activeMeetingId : "없음", true)
                .addField("음성 연결", buildVoiceStatus(guild), true);
    }

    private void showProjectDashboardOrPicker(Guild guild, MessageChannel channel) {
        JsonNode connectedProject = fetchData("/api/v1/projects/channel/" + channel.getId(), "채널 프로젝트 조회");
        if (connectedProject != null) {
            sendProjectDashboard(channel, guild, connectedProject);
            return;
        }

        JsonNode projects = fetchData("/api/v1/projects", "프로젝트 목록 조회");
        List<ActionRow> components = new ArrayList<>();
        components.add(ActionRow.of(Button.secondary(BUTTON_PROJECT_CREATE, "프로젝트 생성")));
        if (projects != null && projects.isArray() && !projects.isEmpty()) {
            components.add(ActionRow.of(buildProjectSelectMenu(projects)));
        }

        channel.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(Color.ORANGE)
                        .setTitle("이 채널에 연결된 프로젝트가 없어요")
                        .setDescription("새 프로젝트를 만들거나 기존 프로젝트 대시보드를 선택해 확인할 수 있어요.")
                        .build())
                .setComponents(components)
                .queue(message -> queueDelete(message, TEMPORARY_MESSAGE_SECONDS));
    }

    private void sendProjectDashboard(MessageChannel channel, Guild guild, JsonNode project) {
        long projectId = project.path("id").asLong();
        JsonNode decisions = fetchData("/api/v1/projects/" + projectId + "/decisions", "프로젝트 결정사항 조회");
        Long connectedProjectId = fetchProjectIdByChannel(channel.getIdLong());
        boolean connected = connectedProjectId != null && connectedProjectId == projectId;
        channel.sendMessageEmbeds(buildProjectDashboardEmbed(guild, project, decisions, connected)
                        .build())
                .queue();
    }

    private StringSelectMenu buildProjectSelectMenu(JsonNode projects) {
        List<SelectOption> options = new ArrayList<>();
        int count = Math.min(projects.size(), PROJECT_SELECT_LIMIT);
        for (int i = 0; i < count; i++) {
            JsonNode project = projects.get(i);
            String id = project.path("id").asText();
            String name = truncate(textOrDefault(project.path("name"), "이름 없는 프로젝트"), 100);
            String description = truncate(textOrDefault(project.path("techStack"), "기술 스택 미입력"), 100);
            options.add(SelectOption.of(name, id).withDescription(description));
        }

        return StringSelectMenu.create(SELECT_PROJECT)
                .setPlaceholder("기존 프로젝트 선택")
                .addOptions(options)
                .build();
    }

    private EmbedBuilder buildProjectDashboardEmbed(
            Guild guild, JsonNode project, JsonNode decisions, boolean connected) {
        Long activeMeetingId = activeMeetingIdByGuild.get(guild.getIdLong());
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(FLODI_COLOR)
                .setTitle("Flodi 대시보드")
                .setDescription(textOrDefault(project.path("name"), "이름 없는 프로젝트"))
                .addField("설명", textOrDefault(project.path("description"), "-"), false)
                .addField("기술 스택", textOrDefault(project.path("techStack"), "-"), false)
                .addField("생성일", textOrDefault(project.path("createdAt"), "-"), true)
                .addField("연결 상태", connected ? "현재 채널에 연결됨" : "다른 채널 또는 미연결", true)
                .addField("활성 회의", activeMeetingId != null ? "meetingId=" + activeMeetingId : "없음", true)
                .addField("음성 연결", buildVoiceStatus(guild), true);

        embed.addField("최근 결정사항", formatDecisionPreview(decisions), false);
        return embed;
    }

    private Modal buildProjectCreateModal() {
        TextInput name = TextInput.create(MODAL_PROJECT_NAME, TextInputStyle.SHORT)
                .setPlaceholder("예: Flodi")
                .setRequired(true)
                .setRequiredRange(1, 80)
                .build();
        TextInput description = TextInput.create(MODAL_PROJECT_DESCRIPTION, TextInputStyle.PARAGRAPH)
                .setPlaceholder("프로젝트 설명")
                .setRequired(false)
                .setRequiredRange(0, 500)
                .build();
        TextInput techStack = TextInput.create(MODAL_PROJECT_TECH_STACK, TextInputStyle.SHORT)
                .setPlaceholder("예: Spring Boot, PostgreSQL, Discord")
                .setRequired(false)
                .setRequiredRange(0, 200)
                .build();

        return Modal.create(MODAL_PROJECT_CREATE, "프로젝트 생성")
                .addComponents(Label.of("프로젝트 이름", name), Label.of("설명", description), Label.of("기술 스택", techStack))
                .build();
    }

    private JsonNode createProjectFromModal(ModalInteractionEvent event) {
        String name = modalValue(event, MODAL_PROJECT_NAME);
        if (name.isBlank()) {
            return null;
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        putNullable(body, "description", modalValue(event, MODAL_PROJECT_DESCRIPTION));
        putNullable(body, "techStack", modalValue(event, MODAL_PROJECT_TECH_STACK));
        body.putNull("serverId");
        body.put("channelId", event.getChannel().getId());

        return postData("/api/v1/projects", body, "프로젝트 생성");
    }

    private String buildStatsMessage(Guild guild) {
        AudioManager audioManager = guild.getAudioManager();
        PerUserAudioReceiveHandler handler = receiveHandlers.get(guild.getIdLong());
        if (handler == null) {
            return "현재 수신 중인 음성 세션이 없어. 먼저 회의 시작을 눌러줘.";
        }

        String connectionSummary = "연결 상태: connected="
                + audioManager.isConnected()
                + ", status="
                + audioManager.getConnectionStatus()
                + ", selfMuted="
                + audioManager.isSelfMuted()
                + ", selfDeafened="
                + audioManager.isSelfDeafened();

        return connectionSummary + "\n" + handler.getStatsSummary();
    }

    private String buildVoiceStatus(Guild guild) {
        AudioManager audioManager = guild.getAudioManager();
        PerUserAudioReceiveHandler handler = receiveHandlers.get(guild.getIdLong());
        String receiving = handler == null ? "수신 없음" : "수신 중";
        return "connected="
                + audioManager.isConnected()
                + ", status="
                + audioManager.getConnectionStatus()
                + ", "
                + receiving;
    }

    private JsonNode fetchData(String path, String action) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + path))
                    .GET();
            attachInternalApiKey(requestBuilder);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[봇/API조회실패] action={}, path={}, status={}", action, path, response.statusCode());
                return null;
            }

            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (data.isMissingNode() || data.isNull()) {
                return null;
            }
            return data;
        } catch (Exception exception) {
            log.warn("[봇/API조회예외] action={}, path={}", action, path, exception);
            return null;
        }
    }

    private JsonNode postData(String path, ObjectNode body, String action) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            attachInternalApiKey(requestBuilder);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn(
                        "[봇/API생성실패] action={}, path={}, status={}, body={}",
                        action,
                        path,
                        response.statusCode(),
                        response.body());
                return null;
            }

            JsonNode data = objectMapper.readTree(response.body()).path("data");
            return data.isMissingNode() || data.isNull() ? null : data;
        } catch (Exception exception) {
            log.warn("[봇/API생성예외] action={}, path={}", action, path, exception);
            return null;
        }
    }

    private String modalValue(ModalInteractionEvent event, String id) {
        return event.getValues().stream()
                .filter(value -> id.equals(value.getCustomId()))
                .findFirst()
                .map(value -> value.getAsString().trim())
                .orElse("");
    }

    private void putNullable(ObjectNode body, String field, String value) {
        if (value == null || value.isBlank()) {
            body.putNull(field);
            return;
        }
        body.put(field, value);
    }

    private String formatDecisionPreview(JsonNode decisions) {
        if (decisions == null || !decisions.isArray() || decisions.isEmpty()) {
            return "등록된 결정사항이 없어요.";
        }

        StringBuilder message = new StringBuilder();
        int count = Math.min(decisions.size(), DECISION_PREVIEW_LIMIT);
        for (int i = 0; i < count; i++) {
            JsonNode decision = decisions.get(i);
            message.append("- ")
                    .append(textOrDefault(decision.path("content"), "내용 없음"))
                    .append("\n");
        }
        if (decisions.size() > count) {
            message.append("- 외 ").append(decisions.size() - count).append("개 더 있음");
        }
        return message.toString();
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private static final String PROJECT_COMMANDS = """
            **프로젝트 명령어**
            `!project`                현재 프로젝트 조회
            `!project list`           프로젝트 목록 조회
            `!project start`          새 프로젝트 생성
            `!project start {id}`     기존 프로젝트 선택
            `!project end`            현재 프로젝트 연결 해제
            `!project edit`           프로젝트 정보 수정
            `!project delete`         프로젝트 삭제""";

    private static final String MEETING_COMMANDS = """
            **회의 명령어**
            `!meeting`                현재 회의 조회
            `!meeting list`           현재 프로젝트 회의 목록 조회
            `!meeting start`          회의 시작
            `!meeting end`            회의 종료
            `!meeting delete`         회의 기록 삭제""";

    private void handleHelp(MessageReceivedEvent event) {
        event.getChannel()
                .sendMessage("📋 **Flodi 봇 명령어 안내**\n\n" + PROJECT_COMMANDS + "\n\n" + MEETING_COMMANDS)
                .queue();
    }

    private void handleProject(MessageReceivedEvent event, long channelId) {
        Long projectId = fetchProjectIdByChannel(channelId);
        String header;
        if (projectId == null) {
            header = "⚠️ 연결된 프로젝트가 없어요. `!project start`로 생성하거나 `!project start {id}`로 선택하세요.";
        } else {
            try {
                HttpRequest.Builder req = HttpRequest.newBuilder()
                        .uri(URI.create(internalBaseUrl + "/api/v1/projects/" + projectId))
                        .GET();
                attachInternalApiKey(req);
                HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
                JsonNode data = objectMapper.readTree(response.body()).path("data");
                String name = data.path("name").asText("-");
                String description = data.path("description").asText("-");
                String techStack = data.path("techStack").asText("-");
                header = "📁 **현재 프로젝트**\n이름: " + name + "\n설명: " + description + "\n기술스택: " + techStack;
            } catch (Exception e) {
                log.warn("[프로젝트/조회예외] channelId={}", channelId, e);
                header = "❌ 프로젝트 조회 중 오류가 발생했습니다.";
            }
        }
        event.getChannel().sendMessage(header + "\n\n" + PROJECT_COMMANDS).queue();
    }

    private void handleMeeting(MessageReceivedEvent event) {
        long guildId = event.getGuild().getIdLong();
        Long meetingId = activeMeetingIdByGuild.get(guildId);
        String header = meetingId != null
                ? "🎙️ **현재 회의 진행 중** (meetingId=" + meetingId + ")"
                : "현재 진행 중인 회의가 없어요. `!meeting start`로 시작하세요.";
        event.getChannel().sendMessage(header + "\n\n" + MEETING_COMMANDS).queue();
    }

    private void handleProjectList(MessageReceivedEvent event) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/projects"))
                    .GET();
            attachInternalApiKey(req);
            HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                event.getChannel().sendMessage("📋 등록된 프로젝트가 없어요.").queue();
                return;
            }
            StringBuilder sb = new StringBuilder("📋 **프로젝트 목록**\n");
            for (JsonNode p : data) {
                sb.append("`id=").append(p.path("id").asText()).append("` ");
                sb.append(p.path("name").asText()).append("\n");
            }
            event.getChannel().sendMessage(sb.toString().trim()).queue();
        } catch (Exception e) {
            log.warn("[프로젝트/목록예외]", e);
            event.getChannel().sendMessage("❌ 프로젝트 목록 조회 중 오류가 발생했습니다.").queue();
        }
    }

    private void handleProjectStartWithArg(MessageReceivedEvent event, long channelId, String arg) {
        try {
            // id로 조회 시도, 실패하면 name으로 검색
            Long projectId = null;
            try {
                long id = Long.parseLong(arg);
                HttpRequest.Builder req = HttpRequest.newBuilder()
                        .uri(URI.create(internalBaseUrl + "/api/v1/projects/" + id))
                        .GET();
                attachInternalApiKey(req);
                HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    projectId = objectMapper
                            .readTree(resp.body())
                            .path("data")
                            .path("id")
                            .asLong();
                }
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 name으로 검색
                HttpRequest.Builder req = HttpRequest.newBuilder()
                        .uri(URI.create(internalBaseUrl + "/api/v1/projects"))
                        .GET();
                attachInternalApiKey(req);
                HttpResponse<String> resp = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
                JsonNode list = objectMapper.readTree(resp.body()).path("data");
                for (JsonNode p : list) {
                    if (arg.equalsIgnoreCase(p.path("name").asText())) {
                        projectId = p.path("id").asLong();
                        break;
                    }
                }
            }

            if (projectId == null) {
                event.getChannel()
                        .sendMessage("❌ `" + arg + "`에 해당하는 프로젝트를 찾을 수 없어요.")
                        .queue();
                return;
            }

            // 채널에 프로젝트 연결
            HttpRequest.Builder connectReq = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/projects/" + projectId + "/channel/" + channelId))
                    .PUT(HttpRequest.BodyPublishers.noBody());
            attachInternalApiKey(connectReq);
            HttpResponse<String> connectResp =
                    httpClient.send(connectReq.build(), HttpResponse.BodyHandlers.ofString());
            if (connectResp.statusCode() / 100 != 2) {
                event.getChannel().sendMessage("❌ 프로젝트 연결에 실패했습니다.").queue();
                return;
            }

            JsonNode data = objectMapper.readTree(connectResp.body());
            event.getChannel()
                    .sendMessage("✅ 프로젝트 (id=" + projectId + ")에 이 채널을 연결했습니다.")
                    .queue();
            log.info("[프로젝트/채널연결] projectId={}, channelId={}", projectId, channelId);
        } catch (Exception e) {
            log.warn("[프로젝트/선택예외] arg={}", arg, e);
            event.getChannel().sendMessage("❌ 프로젝트 선택 중 오류가 발생했습니다.").queue();
        }
    }

    private void handleProjectEnd(MessageReceivedEvent event, long channelId) {
        Long projectId = fetchProjectIdByChannel(channelId);
        if (projectId == null) {
            event.getChannel().sendMessage("이 채널에 연결된 프로젝트가 없어요.").queue();
            return;
        }
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/projects/" + projectId + "/channel"))
                    .DELETE();
            attachInternalApiKey(req);
            HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                event.getChannel().sendMessage("❌ 프로젝트 연결 해제에 실패했습니다.").queue();
                return;
            }
            event.getChannel().sendMessage("✅ 프로젝트 연결이 해제됐습니다.").queue();
            log.info("[프로젝트/채널해제] projectId={}, channelId={}", projectId, channelId);
        } catch (Exception e) {
            log.warn("[프로젝트/채널해제예외] projectId={}", projectId, e);
            event.getChannel().sendMessage("❌ 프로젝트 연결 해제 중 오류가 발생했습니다.").queue();
        }
    }

    private void handleProjectEdit(MessageReceivedEvent event, long channelId) {
        Long projectId = fetchProjectIdByChannel(channelId);
        if (projectId == null) {
            event.getChannel()
                    .sendMessage("이 채널에 연결된 프로젝트가 없어요. `!project start {id}`로 프로젝트를 선택해주세요.")
                    .queue();
            return;
        }
        pendingProjectEdits.put(
                channelId,
                new ProjectEditState(
                        ProjectCreationStep.WAITING_NAME, null, null, projectId, System.currentTimeMillis()));
        event.getChannel().sendMessage("새 프로젝트 이름을 입력해주세요. (건너뛰려면 '.')").queue();
    }

    private void handleProjectDelete(MessageReceivedEvent event, long channelId) {
        Long projectId = fetchProjectIdByChannel(channelId);
        if (projectId == null) {
            event.getChannel().sendMessage("이 채널에 연결된 프로젝트가 없어요.").queue();
            return;
        }
        pendingProjectDeletions.put(channelId, new ProjectDeletionState(projectId, System.currentTimeMillis()));
        event.getChannel()
                .sendMessage("⚠️ 정말로 프로젝트를 삭제하시겠어요? 이 작업은 되돌릴 수 없습니다. (yes / no)")
                .queue();
    }

    private void handleProjectEditStep(MessageReceivedEvent event, long channelId) {
        ProjectEditState state = pendingProjectEdits.get(channelId);

        if (System.currentTimeMillis() - state.createdAt() > PENDING_TTL_MS) {
            pendingProjectEdits.remove(channelId);
            event.getChannel()
                    .sendMessage("⏰ 수정 시간이 초과됐습니다. 다시 `!project edit`를 입력해주세요.")
                    .queue();
            return;
        }

        String input = event.getMessage().getContentRaw().trim();
        String value = ".".equals(input) ? null : input;

        switch (state.step()) {
            case WAITING_NAME -> {
                pendingProjectEdits.put(
                        channelId,
                        new ProjectEditState(
                                ProjectCreationStep.WAITING_DESCRIPTION,
                                value,
                                null,
                                state.projectId(),
                                state.createdAt()));
                event.getChannel().sendMessage("설명을 입력해주세요. (건너뛰려면 '.')").queue();
            }
            case WAITING_DESCRIPTION -> {
                pendingProjectEdits.put(
                        channelId,
                        new ProjectEditState(
                                ProjectCreationStep.WAITING_TECH_STACK,
                                state.name(),
                                value,
                                state.projectId(),
                                state.createdAt()));
                event.getChannel().sendMessage("기술 스택을 입력해주세요. (건너뛰려면 '.')").queue();
            }
            case WAITING_TECH_STACK -> {
                pendingProjectEdits.remove(channelId);
                try {
                    ObjectNode body = objectMapper.createObjectNode();
                    body.put("name", state.name());
                    body.put("description", state.description());
                    body.put("techStack", value);

                    HttpRequest.Builder req = HttpRequest.newBuilder()
                            .uri(URI.create(internalBaseUrl + "/api/v1/projects/" + state.projectId()))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
                    attachInternalApiKey(req);
                    HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() / 100 != 2) {
                        event.getChannel().sendMessage("❌ 프로젝트 수정에 실패했습니다.").queue();
                        return;
                    }
                    event.getChannel().sendMessage("✅ 프로젝트가 수정됐습니다.").queue();
                } catch (Exception e) {
                    log.warn("[프로젝트/수정예외] projectId={}", state.projectId(), e);
                    event.getChannel().sendMessage("❌ 프로젝트 수정 중 오류가 발생했습니다.").queue();
                }
            }
        }
    }

    private void handleProjectDeletionStep(MessageReceivedEvent event, long channelId) {
        ProjectDeletionState state = pendingProjectDeletions.get(channelId);

        if (System.currentTimeMillis() - state.createdAt() > PENDING_TTL_MS) {
            pendingProjectDeletions.remove(channelId);
            event.getChannel()
                    .sendMessage("⏰ 응답 시간이 초과됐습니다. 다시 `!project delete`를 입력해주세요.")
                    .queue();
            return;
        }

        String input = event.getMessage().getContentRaw().trim().toLowerCase();
        pendingProjectDeletions.remove(channelId);

        if ("yes".equals(input)) {
            try {
                HttpRequest.Builder req = HttpRequest.newBuilder()
                        .uri(URI.create(internalBaseUrl + "/api/v1/projects/" + state.projectId()))
                        .DELETE();
                attachInternalApiKey(req);
                HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    event.getChannel().sendMessage("❌ 프로젝트 삭제에 실패했습니다.").queue();
                    return;
                }
                event.getChannel().sendMessage("✅ 프로젝트가 삭제됐습니다.").queue();
                log.info("[프로젝트/삭제] projectId={}", state.projectId());
            } catch (Exception e) {
                log.warn("[프로젝트/삭제예외] projectId={}", state.projectId(), e);
                event.getChannel().sendMessage("❌ 프로젝트 삭제 중 오류가 발생했습니다.").queue();
            }
        } else if ("no".equals(input)) {
            event.getChannel().sendMessage("프로젝트 삭제를 취소했습니다.").queue();
        } else {
            event.getChannel()
                    .sendMessage("'yes' 또는 'no'로 답해주세요. 다시 `!project delete`를 입력해주세요.")
                    .queue();
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
                    .sendMessage("프로젝트 생성 시간이 초과됐습니다. 다시 `!project start`를 입력해주세요.")
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
                            .sendMessage("프로젝트 [" + state.name() + "]가 생성됐습니다! (id=" + projectId + ")")
                            .queue();
                }
            }
        }
    }

    private void handleMeetingStart(MeetingStartContext ctx) {
        Long projectId = fetchProjectIdByChannel(ctx.channelId());
        if (projectId != null) {
            startMeeting(ctx, projectId);
            return;
        }

        // 채널에 연결된 프로젝트가 없으면 텍스트 확인을 요청한다.
        pendingMeetingConfirmations.put(ctx.channelId(), new MeetingConfirmationState(ctx, System.currentTimeMillis()));
        ctx.textChannel()
                .sendMessage("이 채널에 연결된 프로젝트가 없어서 회의가 저장되지 않아요.\n" + "그래도 진행하시겠어요? (yes / no)")
                .queue();
    }

    private void handleMeetingConfirmationStep(MessageReceivedEvent event, long channelId) {
        MeetingConfirmationState state = pendingMeetingConfirmations.get(channelId);

        if (System.currentTimeMillis() - state.createdAt() > PENDING_TTL_MS) {
            pendingMeetingConfirmations.remove(channelId);
            event.getChannel()
                    .sendMessage("응답 시간이 초과됐습니다. 다시 `!meeting start`를 입력해주세요.")
                    .queue();
            return;
        }

        String input = event.getMessage().getContentRaw().trim().toLowerCase();
        pendingMeetingConfirmations.remove(channelId);

        if ("yes".equals(input)) {
            MeetingStartContext ctx = state.ctx();
            new Thread(() -> startMeeting(ctx, null)).start();
        } else if ("no".equals(input)) {
            event.getChannel().sendMessage("회의 시작을 취소했습니다.").queue();
        } else {
            event.getChannel()
                    .sendMessage("'yes' 또는 'no'로 답해주세요. 다시 `!meeting start`를 입력해주세요.")
                    .queue();
        }
    }

    private void handleMeetingEnd(MessageReceivedEvent event) {
        MeetingEndResult result = endActiveMeeting(event.getGuild());
        sendTemporaryMessage(event.getChannel(), buildMeetingEndMessage(result));
    }

    private MeetingEndResult endActiveMeeting(Guild guild) {
        long guildId = guild.getIdLong();
        AudioManager audioManager = guild.getAudioManager();
        PerUserAudioReceiveHandler handler = receiveHandlers.remove(guildId);
        Long meetingId = activeMeetingIdByGuild.remove(guildId);
        boolean hadVoiceState = handler != null || audioManager.isConnected();

        if (handler != null) {
            handler.closeAllSttSessions();
        }

        audioManager.closeAudioConnection();
        audioManager.setReceivingHandler(null);
        audioManager.setConnectionListener(null);

        if (meetingId != null) {
            endMeeting(guildId, meetingId);
        }
        log.info("[미팅/종료명령] guildId={}, meetingId={}, hadVoiceState={}", guildId, meetingId, hadVoiceState);
        return new MeetingEndResult(meetingId, hadVoiceState);
    }

    private void handleMeetingList(MessageReceivedEvent event, long channelId) {
        Long projectId = fetchProjectIdByChannel(channelId);
        if (projectId == null) {
            event.getChannel()
                    .sendMessage("이 채널에 연결된 프로젝트가 없어요. `!project start {id}`로 프로젝트를 선택해주세요.")
                    .queue();
            return;
        }
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/api/v1/meetings?projectId=" + projectId))
                    .GET();
            attachInternalApiKey(req);
            HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                event.getChannel().sendMessage("📋 프로젝트에 등록된 회의가 없어요.").queue();
                return;
            }
            StringBuilder sb = new StringBuilder("📋 **회의 목록** (projectId=" + projectId + ")\n");
            for (JsonNode m : data) {
                sb.append("`id=").append(m.path("id").asText()).append("` ");
                sb.append(m.path("title").asText()).append(" | ");
                sb.append(m.path("startedAt").asText("")).append(" ~ ");
                sb.append(m.path("endedAt").asText("진행 중")).append("\n");
            }
            event.getChannel().sendMessage(sb.toString().trim()).queue();
        } catch (Exception e) {
            log.warn("[회의/목록예외] channelId={}", channelId, e);
            event.getChannel().sendMessage("❌ 회의 목록 조회 중 오류가 발생했습니다.").queue();
        }
    }

    private void handleMeetingDelete(MessageReceivedEvent event, String arg) {
        long meetingId;
        try {
            meetingId = Long.parseLong(arg);
        } catch (NumberFormatException e) {
            event.getChannel().sendMessage("사용법: `!meeting delete {id}`").queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        Long activeMeetingId = activeMeetingIdByGuild.get(guildId);
        if (activeMeetingId != null && activeMeetingId == meetingId) {
            event.getChannel()
                    .sendMessage("진행 중인 회의는 삭제할 수 없어요. 먼저 `!meeting end`로 종료해주세요.")
                    .queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        pendingMeetingDeletions.put(channelId, new MeetingDeletionState(meetingId, System.currentTimeMillis()));
        event.getChannel()
                .sendMessage("⚠️ 회의 (id=" + meetingId + ")를 삭제하시겠어요? 이 작업은 되돌릴 수 없습니다. (yes / no)")
                .queue();
    }

    private void handleMeetingDeletionStep(MessageReceivedEvent event, long channelId) {
        MeetingDeletionState state = pendingMeetingDeletions.get(channelId);

        if (System.currentTimeMillis() - state.createdAt() > PENDING_TTL_MS) {
            pendingMeetingDeletions.remove(channelId);
            event.getChannel()
                    .sendMessage("⏰ 응답 시간이 초과됐습니다. 다시 `!meeting delete {id}`를 입력해주세요.")
                    .queue();
            return;
        }

        String input = event.getMessage().getContentRaw().trim().toLowerCase();
        pendingMeetingDeletions.remove(channelId);

        if ("yes".equals(input)) {
            try {
                HttpRequest.Builder req = HttpRequest.newBuilder()
                        .uri(URI.create(internalBaseUrl + "/api/v1/meetings/" + state.meetingId()))
                        .DELETE();
                attachInternalApiKey(req);
                HttpResponse<String> response = httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    event.getChannel().sendMessage("❌ 회의 삭제에 실패했습니다.").queue();
                    return;
                }
                event.getChannel()
                        .sendMessage("✅ 회의 (id=" + state.meetingId() + ")가 삭제됐습니다.")
                        .queue();
                log.info("[회의/삭제] meetingId={}", state.meetingId());
            } catch (Exception e) {
                log.warn("[회의/삭제예외] meetingId={}", state.meetingId(), e);
                event.getChannel().sendMessage("❌ 회의 삭제 중 오류가 발생했습니다.").queue();
            }
        } else if ("no".equals(input)) {
            event.getChannel().sendMessage("회의 삭제를 취소했습니다.").queue();
        } else {
            event.getChannel()
                    .sendMessage("'yes' 또는 'no'로 답해주세요. 다시 `!meeting delete {id}`를 입력해주세요.")
                    .queue();
        }
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

    private void startMeeting(MeetingStartContext ctx, Long projectId) {
        if (activeMeetingIdByGuild.containsKey(ctx.guildId())) {
            ctx.textChannel().sendMessage("이미 진행 중인 회의가 있어요. 먼저 회의 종료를 눌러주세요.").queue();
            return;
        }
        if (!startingGuildIds.add(ctx.guildId())) {
            ctx.textChannel().sendMessage("회의 시작 요청을 처리 중이에요. 잠시만 기다려주세요.").queue();
            return;
        }

        Long meetingId = createMeeting(ctx.guildId(), ctx.guildName(), projectId);
        try {
            if (meetingId == null) {
                ctx.textChannel().sendMessage("회의 생성 실패. 서버 로그를 확인해주세요.").queue();
                return;
            }

            PerUserAudioReceiveHandler existing = receiveHandlers.remove(ctx.guildId());
            if (existing != null) {
                existing.closeAllSttSessions();
            }

            PerUserAudioReceiveHandler handler =
                    new PerUserAudioReceiveHandler(ctx.guildId(), ctx.guild(), sttProvider, meetingId);
            receiveHandlers.put(ctx.guildId(), handler);
            activeMeetingIdByGuild.put(ctx.guildId(), meetingId);
            handler.updateCaptionChannel(ctx.textChannel());

            ctx.audioManager().setReceivingHandler(handler);
            ctx.audioManager().setConnectionListener(new LoggingConnectionListener(ctx.guildId(), ctx.voiceChannel()));
            ctx.audioManager().setAutoReconnect(true);
            ctx.audioManager().setSelfMuted(false);
            ctx.audioManager().setSelfDeafened(false);
            ctx.audioManager().openAudioConnection(ctx.voiceChannel());

            ctx.textChannel()
                    .sendMessage(
                            "회의 시작! 음성 채널 [" + ctx.voiceChannel().getName() + "]에 입장했어요. (meetingId=" + meetingId + ")")
                    .queue();
            log.info(
                    "[미팅/시작] guildId={}, meetingId={}, projectId={}, channel={}",
                    ctx.guildId(),
                    meetingId,
                    projectId,
                    ctx.voiceChannel().getName());
        } finally {
            startingGuildIds.remove(ctx.guildId());
        }
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
                        .sendMessage("프로젝트 생성에 실패했습니다. 서버 로그를 확인해주세요.")
                        .queue();
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode idNode = root.path("data").path("id");
            if (!idNode.isNumber()) {
                log.warn("[프로젝트/생성응답오류] channelId={}, body={}", channelId, response.body());
                event.getChannel().sendMessage("프로젝트 생성 응답 오류입니다.").queue();
                return null;
            }

            long projectId = idNode.asLong();
            log.info("[프로젝트/생성성공] channelId={}, projectId={}", channelId, projectId);
            return projectId;
        } catch (Exception e) {
            log.warn("[프로젝트/생성예외] channelId={}", channelId, e);
            event.getChannel().sendMessage("프로젝트 생성 중 오류가 발생했습니다.").queue();
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
            event.getChannel()
                    .sendMessage("회의 생성 실패로 입장을 중단했어요. 서버 로그를 확인해주세요.")
                    .queue();
            return;
        }

        AudioChannel targetChannel = member.getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();

        PerUserAudioReceiveHandler existing = receiveHandlers.remove(guildId);
        if (existing != null) {
            existing.closeAllSttSessions();
        }

        // 길드별 핸들러는 하나만 유지한다.
        // Discord 특성상 봇 하나는 길드 내 음성 채널 하나에만 연결된다.
        PerUserAudioReceiveHandler handler =
                new PerUserAudioReceiveHandler(guildId, event.getGuild(), sttProvider, meetingId);
        receiveHandlers.put(guildId, handler);
        activeMeetingIdByGuild.put(guildId, meetingId);
        handler.updateCaptionChannel(event.getChannel());

        // 오디오 수신 핸들러 연결 및 음성 채널 연결
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
                        + " (오디오 연결은 비동기로 진행됨"
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
        MeetingEndResult result = endActiveMeeting(event.getGuild());
        sendTemporaryMessage(event.getChannel(), buildMeetingEndMessage(result));
    }

    private void handleStats(MessageReceivedEvent event) {
        sendTemporaryMessage(event.getChannel(), buildStatsMessage(event.getGuild()));
    }

    private void sendTemporaryMessage(MessageChannel channel, String content) {
        channel.sendMessage(content).queue(message -> queueDelete(message, TEMPORARY_MESSAGE_SECONDS));
    }

    private void queueDelete(Message message, long seconds) {
        message.delete().queueAfter(seconds, TimeUnit.SECONDS, null, ignored -> {});
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

    private String buildMeetingEndMessage(MeetingEndResult result) {
        if (result.meetingId() != null) {
            return "회의가 종료됐고 음성 연결도 정리했어요. 요약을 생성 중이에요... (meetingId=" + result.meetingId() + ")";
        }
        if (result.hadVoiceState()) {
            return "저장 중인 회의는 없었지만 남아 있던 음성 연결은 정리했어요.";
        }
        return "이 봇 인스턴스에는 진행 중인 회의나 음성 연결이 없어요.";
    }

    private boolean isAllowedGuild(long guildId) {
        return allowedGuildIds.isEmpty() || allowedGuildIds.contains(guildId);
    }

    static Set<Long> parseAllowedGuildIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Long.parseLong(trimmed));
            } catch (NumberFormatException exception) {
                log.warn("[봇/서버필터무시] value={}, reason=invalid_guild_id", trimmed);
            }
        }
        return Set.copyOf(result);
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
