package com.flodiback.domain.meeting.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.ai.service.AiChatService;
import com.flodiback.domain.meeting.analysis.dto.AnalysisResult;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository.SpeakerProjection;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.worklog.entity.WorkLog;
import com.flodiback.domain.project.worklog.repository.WorkLogRepository;
import com.flodiback.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class MeetingAnalysisServiceTest {

    @InjectMocks
    private MeetingAnalysisService service;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private ContextCacheRepository contextCacheRepository;

    @Mock
    private UtteranceRepository utteranceRepository;

    @Mock
    private WorkLogRepository workLogRepository;

    @Mock
    private MeetingContextPersistenceService meetingContextPersistenceService;

    @Mock
    private AiChatService aiChatService;

    @Mock(name = "objectMapper")
    private ObjectMapper objectMapper;

    private final ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "systemPrompt", "test-system-prompt");
        lenient()
                .when(workLogRepository.findByProjectIdOrderByCreatedAtDesc(anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void analyze_throwsWhenMeetingDoesNotExist() {
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 회의입니다.");
    }

    @Test
    void analyze_withoutCache_persistsSummaryEmbeddingAndDerivedItemsInOrder() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        Utterance u1 = utterance(meeting, 1L, "Alice", "API spec is decided.", 1);
        Utterance u2 = utterance(meeting, 2L, "Bob", "I will finish implementation by Friday.", 2);
        MeetingSummary savedSummary =
                MeetingSummary.builder().meeting(meeting).summary("saved").build();
        String glmResponse = """
                {
                  "summary": "API spec and implementation schedule were discussed.",
                  "unresolvedItems": null,
                  "worklogs": [
                    { "assigneeSpeakerKey": "S2", "task": "Finish implementation", "dueDate": "2026-05-04" },
                    { "assigneeSpeakerKey": "S1", "task": "Write tests", "dueDate": null }
                  ],
                  "decisions": [
                    { "content": "Finalize API spec" }
                  ]
                }
                """;

        givenAnalysisInputs(meeting, List.of(u1, u2), glmResponse);
        given(meetingContextPersistenceService.saveSummaryRequired(any(), any()))
                .willReturn(savedSummary);

        service.analyze(1L);

        ArgumentCaptor<UpdateContextRequest> captor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        InOrder ordered = inOrder(meetingContextPersistenceService);
        ordered.verify(meetingContextPersistenceService)
                .saveSummaryRequired(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        ordered.verify(meetingContextPersistenceService).saveSummaryEmbeddingBestEffort(savedSummary);
        ordered.verify(meetingContextPersistenceService)
                .saveDerivedItemsBestEffort(org.mockito.ArgumentMatchers.eq(10L), any(UpdateContextRequest.class));

        UpdateContextRequest saved = captor.getValue();
        assertThat(saved.summary()).isEqualTo("API spec and implementation schedule were discussed.");
        assertThat(saved.unresolvedItems()).isNull();
        assertThat(saved.decisions()).containsExactly("Finalize API spec");
        assertThat(saved.actionItems()).hasSize(2);
        assertThat(saved.actionItems().get(0).assigneeName()).isEqualTo("Bob");
        assertThat(saved.actionItems().get(0).assigneeDiscordId()).isEqualTo("user-2");
        assertThat(saved.actionItems().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 5, 4));
        assertThat(saved.actionItems().get(1).assigneeName()).isEqualTo("Alice");
        assertThat(saved.actionItems().get(1).assigneeDiscordId()).isEqualTo("user-1");
        assertThat(saved.actionItems().get(1).dueDate()).isNull();
    }

    @Test
    void analyze_normalizesAiGeneratedDerivedItems() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        String glmResponse = """
                {
                  "summary": "  summary  ",
                  "unresolvedItems": "  unresolved  ",
                  "worklogs": [
                    { "assigneeSpeakerKey": null, "task": "  keep task  ", "dueDate": null },
                    { "assigneeSpeakerKey": "S1", "task": "   ", "dueDate": null }
                  ],
                  "decisions": [
                    { "content": "  keep decision  " },
                    { "content": "   " },
                    { "content": null }
                  ]
                }
                """;

        givenAnalysisInputs(meeting, List.of(), glmResponse);

        service.analyze(1L);

        ArgumentCaptor<UpdateContextRequest> captor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        verify(meetingContextPersistenceService)
                .saveSummaryRequired(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        UpdateContextRequest request = captor.getValue();
        assertThat(request.summary()).isEqualTo("summary");
        assertThat(request.unresolvedItems()).isEqualTo("unresolved");
        assertThat(request.decisions()).containsExactly("keep decision");
        assertThat(request.actionItems()).hasSize(1);
        assertThat(request.actionItems().get(0).assigneeName()).isEqualTo("담당자 미정");
        assertThat(request.actionItems().get(0).assigneeDiscordId()).isNull();
        assertThat(request.actionItems().get(0).task()).isEqualTo("keep task");
    }

    @Test
    void analyze_acceptsUnresolvedItemsArrayFromAiResponse() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        String aiResponse = """
                {
                  "summary": "summary",
                  "unresolvedItems": [
                    "비행기 및 배편 가격 확정",
                    "   ",
                    null,
                    "렌터카 예약 여부 확인"
                  ],
                  "worklogs": [],
                  "decisions": []
                }
                """;

        givenAnalysisInputs(meeting, List.of(), aiResponse);

        service.analyze(1L);

        ArgumentCaptor<UpdateContextRequest> captor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        verify(meetingContextPersistenceService)
                .saveSummaryRequired(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        assertThat(captor.getValue().unresolvedItems()).isEqualTo("비행기 및 배편 가격 확정\n렌터카 예약 여부 확인");
    }

    @Test
    void analyze_assignsSpeakerKeysByFirstUtteranceIdAndFormatsPrompt() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        Utterance bobFirst = utterance(meeting, 1L, "Bob", "I can do API.", 1);
        Utterance aliceSecond = utterance(meeting, 2L, "Alice", "I can test it.", 2);

        givenAnalysisInputs(meeting, List.of(aliceSecond, bobFirst), emptyAnalysisJson());

        service.analyze(1L);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateSummary(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue())
                .contains("[speaker=S1, name=Bob] I can do API.")
                .contains("[speaker=S2, name=Alice] I can test it.")
                .containsSubsequence("I can do API.", "I can test it.");
    }

    @Test
    void analyze_unknownSpeakerKeyFallsBackToUnknownAssignee() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        String glmResponse = """
                {
                  "summary": "summary",
                  "unresolvedItems": null,
                  "worklogs": [
                    { "assigneeSpeakerKey": "S99", "task": "keep task", "dueDate": null }
                  ],
                  "decisions": []
                }
                """;

        givenAnalysisInputs(meeting, List.of(), glmResponse);

        service.analyze(1L);

        ArgumentCaptor<UpdateContextRequest> captor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        verify(meetingContextPersistenceService)
                .saveSummaryRequired(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        assertThat(captor.getValue().actionItems()).hasSize(1);
        assertThat(captor.getValue().actionItems().get(0).assigneeName()).isEqualTo("담당자 미정");
        assertThat(captor.getValue().actionItems().get(0).assigneeDiscordId()).isNull();
    }

    @Test
    void analyze_utteranceWithoutSpeakerDiscordIdUsesUnknownSpeakerInPrompt() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        Utterance utterance = utterance(meeting, 1L, "Guest", "I will check it.", 1);
        ReflectionTestUtils.setField(utterance, "speakerDiscordId", null);

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findDistinctSpeakersByMeeting(meeting)).willReturn(List.of());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(List.of(utterance));
        given(aiChatService.generateSummary(anyString(), anyString())).willReturn(emptyAnalysisJson());
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        service.analyze(1L);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateSummary(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue()).contains("[speaker=unknown, name=Guest] I will check it.");
    }

    @Test
    void analyze_blankSummary_doesNotPersistAnything() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        String glmResponse = """
                {
                  "summary": "   ",
                  "unresolvedItems": null,
                  "worklogs": [],
                  "decisions": []
                }
                """;

        givenAnalysisInputs(meeting, List.of(), glmResponse);

        assertThatThrownBy(() -> service.analyze(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("summary is blank");
        verify(meetingContextPersistenceService, never()).saveSummaryRequired(any(), any());
        verify(meetingContextPersistenceService, never()).saveSummaryEmbeddingBestEffort(any());
        verify(meetingContextPersistenceService, never()).saveDerivedItemsBestEffort(any(), any());
    }

    @Test
    void analyze_withLatestCache_usesOnlyLatestSummaryAndLaterUtterances() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        ContextCache oldCache = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("old summary")
                .tokenCount(100)
                .compressedUntilUtteranceId(3L)
                .build();
        ContextCache latestCache = ContextCache.builder()
                .meeting(meeting)
                .version(2)
                .compressedText("latest summary")
                .tokenCount(100)
                .compressedUntilUtteranceId(10L)
                .build();
        Utterance later = utterance(meeting, 11L, "Alice", "later utterance", 2);

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(latestCache));
        given(utteranceRepository.findDistinctSpeakersByMeeting(meeting))
                .willReturn(List.of(speaker("user-11", "Alice", 11L)));
        given(utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(meeting, 10L))
                .willReturn(List.of(later));
        given(aiChatService.generateSummary(anyString(), anyString())).willReturn(emptyAnalysisJson());
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        service.analyze(1L);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateSummary(anyString(), userPromptCaptor.capture());
        String userPrompt = userPromptCaptor.getValue();
        assertThat(userPrompt).contains("latest summary");
        assertThat(userPrompt).doesNotContain("old summary");
        assertThat(userPrompt).contains("[speaker=S1, name=Alice] later utterance");
        assertThat(oldCache.getVersion()).isEqualTo(1);
    }

    @Test
    void analyze_sortsContextUtterancesBySpeechStartedAtThenId() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        Utterance second = utterance(meeting, 2L, "B", "second", 20);
        Utterance first = utterance(meeting, 1L, "A", "first", 10);

        givenAnalysisInputs(meeting, List.of(second, first), emptyAnalysisJson());

        service.analyze(1L);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateSummary(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue()).containsSubsequence("first", "second");
    }

    @Test
    void analyze_stripsMarkdownJsonCodeBlock() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        String glmResponseWithCodeBlock = """
                ```json
                {
                  "summary": "markdown response",
                  "unresolvedItems": "open issue",
                  "worklogs": [],
                  "decisions": []
                }
                ```""";

        givenAnalysisInputs(meeting, List.of(), glmResponseWithCodeBlock);

        service.analyze(1L);

        ArgumentCaptor<UpdateContextRequest> captor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        verify(meetingContextPersistenceService)
                .saveSummaryRequired(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        assertThat(captor.getValue().summary()).isEqualTo("markdown response");
        assertThat(captor.getValue().unresolvedItems()).isEqualTo("open issue");
    }

    @Test
    void analyze_includesExistingWorkLogsAndPassesValidStatusUpdates() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        WorkLog existing = workLog(meeting, project, 7L, "Alice", "write API");
        String glmResponse = """
                {
                  "summary": "summary",
                  "unresolvedItems": null,
                  "worklogs": [],
                  "worklogUpdates": [
                    { "id": 7, "status": "done" },
                    { "id": 999, "status": "DONE" },
                    { "id": 7, "status": "INVALID" }
                  ],
                  "decisions": []
                }
                """;

        givenAnalysisInputs(meeting, List.of(), glmResponse);
        given(workLogRepository.findByProjectIdOrderByCreatedAtDesc(10L)).willReturn(List.of(existing));

        service.analyze(1L);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatService).generateSummary(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue())
                .contains("[프로젝트 기존 작업 항목]")
                .contains("ID:7")
                .contains("작업:\"write API\"")
                .contains("상태:TODO");

        ArgumentCaptor<UpdateContextRequest> requestCaptor = ArgumentCaptor.forClass(UpdateContextRequest.class);
        verify(meetingContextPersistenceService)
                .saveSummaryRequired(org.mockito.ArgumentMatchers.eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().worklogUpdates())
                .extracting(
                        UpdateContextRequest.WorkLogStatusUpdate::id, UpdateContextRequest.WorkLogStatusUpdate::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(7L, "DONE"));
    }

    @Test
    void analyze_throwsWhenGlmResponseCannotBeParsed() throws Exception {
        Project project = mockProject(10L);
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();

        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findDistinctSpeakersByMeeting(meeting)).willReturn(List.of());
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(List.of());
        given(aiChatService.generateSummary(anyString(), anyString())).willReturn("not json");
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));

        assertThatThrownBy(() -> service.analyze(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI 응답 파싱 실패");
    }

    @Test
    void analyze_throwsWhenMeetingHasNoProject() {
        Meeting meeting = Meeting.builder().title("meeting").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.analyze(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("회의에 연결된 프로젝트가 없습니다.");
    }

    private void givenAnalysisInputs(Meeting meeting, List<Utterance> utterances, String aiResponse) throws Exception {
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.empty());
        given(utteranceRepository.findDistinctSpeakersByMeeting(meeting)).willReturn(speakersFrom(utterances));
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(utterances);
        given(aiChatService.generateSummary(anyString(), anyString())).willReturn(aiResponse);
        given(objectMapper.readValue(anyString(), any(Class.class)))
                .willAnswer(inv -> realMapper.readValue((String) inv.getArgument(0), AnalysisResult.class));
    }

    private String emptyAnalysisJson() {
        return """
                {
                  "summary": "summary",
                  "unresolvedItems": null,
                  "worklogs": [],
                  "decisions": []
                }
                """;
    }

    private Utterance utterance(Meeting meeting, Long id, String speakerName, String content, long speechOffset) {
        Utterance utterance = Utterance.builder()
                .meeting(meeting)
                .speakerName(speakerName)
                .speakerDiscordId("user-" + id)
                .content(content)
                .speechStartedAt(LocalDateTime.of(2026, 5, 4, 10, 0).plusSeconds(speechOffset))
                .build();
        ReflectionTestUtils.setField(utterance, "id", id);
        return utterance;
    }

    private WorkLog workLog(Meeting meeting, Project project, Long id, String assigneeName, String task) {
        WorkLog workLog = WorkLog.builder()
                .meeting(meeting)
                .project(project)
                .assigneeName(assigneeName)
                .task(task)
                .dueDate(null)
                .build();
        ReflectionTestUtils.setField(workLog, "id", id);
        return workLog;
    }

    private List<SpeakerProjection> speakersFrom(List<Utterance> utterances) {
        return utterances.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Utterance::getSpeakerDiscordId,
                        utterance -> utterance,
                        (left, right) -> left.getId() <= right.getId() ? left : right))
                .values()
                .stream()
                .sorted(Comparator.comparing(Utterance::getId))
                .map(utterance ->
                        speaker(utterance.getSpeakerDiscordId(), utterance.getSpeakerName(), utterance.getId()))
                .toList();
    }

    private SpeakerProjection speaker(String discordId, String name, Long firstUtteranceId) {
        return new SpeakerProjection() {
            @Override
            public String getSpeakerDiscordId() {
                return discordId;
            }

            @Override
            public String getSpeakerName() {
                return name;
            }

            @Override
            public Long getFirstUtteranceId() {
                return firstUtteranceId;
            }
        };
    }

    private Project mockProject(Long id) {
        Project project = org.mockito.Mockito.mock(Project.class);
        org.mockito.Mockito.lenient().when(project.getId()).thenReturn(id);
        return project;
    }
}
