package com.flodiback.domain.meeting.meetinglog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.decision.decision.service.DecisionEmbeddingService;
import com.flodiback.domain.meeting.meeting.context.MeetingStartContext;
import com.flodiback.domain.meeting.meeting.context.MeetingStartContextProvider;
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.ActionItemRequest;
import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.domain.meeting.meetinglog.repository.UtteranceRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.project.worklog.entity.WorkLog;
import com.flodiback.domain.project.worklog.repository.WorkLogRepository;
import com.flodiback.global.embedding.OpenAiEmbeddingClient;
import com.flodiback.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private UtteranceRepository utteranceRepository;

    @Mock
    private ContextCacheRepository contextCacheRepository;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private MeetingSummaryRepository meetingSummaryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkLogRepository workLogRepository;

    @Mock
    private OpenAiEmbeddingClient embeddingClient;

    @Mock
    private DecisionEmbeddingService decisionEmbeddingService;

    @Mock
    private MeetingSummaryEmbeddingService meetingSummaryEmbeddingService;

    @Mock
    private MeetingStartContextProvider meetingStartContextProvider;

    @InjectMocks
    private ContextService contextService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(contextCacheRepository.findTopByMeetingOrderByVersionDesc(any(Meeting.class)))
                .thenReturn(Optional.empty());
        lenient()
                .when(meetingStartContextProvider.getOrCreate(anyLong()))
                .thenAnswer(invocation -> MeetingStartContext.noProject(invocation.getArgument(0)));
    }

    @Test
    void assemble_throws_whenMeetingDoesNotExist() {
        given(meetingRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contextService.assemble(1L, null)).isInstanceOf(ServiceException.class);
    }

    @Test
    void assemble_returnsShortTermAndEmptyQuestionContext_whenProjectDoesNotExist() {
        Meeting meeting = Meeting.builder().title("standalone").build();
        ReflectionTestUtils.setField(meeting, "id", 1L);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findTop30ByMeetingOrderByIdDesc(meeting)).willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.startContext().projectId()).isNull();
        assertThat(result.shortTerm()).isNotNull();
        assertThat(result.questionContext().decisions()).isEmpty();
        assertThat(result.questionContext().pastSummaries()).isEmpty();
    }

    @Test
    void assemble_noCache_usesTokenBudgetButKeepsAtLeastTenUtterances() {
        Meeting meeting = Meeting.builder().title("meeting").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findTop30ByMeetingOrderByIdDesc(meeting)).willReturn(utterances(meeting, 1, 30, 100));

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().recentUtterances()).hasSize(12);
        assertThat(result.shortTerm().recentUtterances().get(0).content()).isEqualTo("content-19");
        assertThat(result.shortTerm().recentUtterances().get(11).content()).isEqualTo("content-30");
    }

    @Test
    void assemble_noCache_returnsMoreThanTwenty_whenTokensFitBudget() {
        Meeting meeting = Meeting.builder().title("meeting").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findTop30ByMeetingOrderByIdDesc(meeting)).willReturn(utterances(meeting, 1, 30, 10));

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().recentUtterances()).hasSize(30);
    }

    @Test
    void assemble_latestCache_returnsRollingSummaryAndUtterancesAfterWatermark() {
        Meeting meeting = Meeting.builder().title("meeting").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        ContextCache cache = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("previous rolling summary")
                .tokenCount(100)
                .compressedUntilUtteranceId(10L)
                .build();
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(cache));
        given(utteranceRepository.findTop30ByMeetingAndIdGreaterThanOrderByIdDesc(meeting, 10L))
                .willReturn(utterances(meeting, 11, 31, 10));

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().rollingSummary()).isEqualTo("previous rolling summary");
        assertThat(result.shortTerm().recentUtterances()).hasSize(21);
        assertThat(result.shortTerm().recentUtterances().get(0).content()).isEqualTo("content-11");
    }

    @Test
    void assemble_projectWithBlankQuestion_usesStartContextAndSkipsQuestionRetrieval() {
        Project project = project(10L, "Flodi", "Java, Spring");
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        MeetingStartContext startContext = startContext(1L, 10L);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingStartContextProvider.getOrCreate(1L)).willReturn(startContext);
        given(utteranceRepository.findTop30ByMeetingOrderByIdDesc(meeting)).willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.startContext()).isEqualTo(startContext);
        assertThat(result.questionContext().decisions()).isEmpty();
        assertThat(result.questionContext().pastSummaries()).isEmpty();
        verify(decisionRepository, never()).findByProjectIdOrderByIdAsc(10L);
        verify(meetingSummaryRepository, never()).findLatestPastByProjectId(10L, 1L, 5);
    }

    @Test
    void assemble_question_usesHybridSearchAndDedupesStartContextItems() {
        Project project = project(10L, "Flodi", "Java");
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        MeetingStartContext startContext = new MeetingStartContext(
                1L,
                10L,
                "Flodi",
                "Java",
                null,
                List.of(new DecisionSummary(1L, "already in start", null)),
                List.of(new PastSummary(11L, "already in start summary", null)),
                null,
                Collections.emptyList());
        Decision duplicateDecision = decision(project, meeting, 1L, "already in start");
        Decision newDecision = decision(project, meeting, 2L, "new related decision");
        MeetingSummary duplicateSummary = meetingSummary(meeting, 11L, "already in start summary");
        MeetingSummary newSummary = meetingSummary(meeting, 12L, "new related summary");
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingStartContextProvider.getOrCreate(1L)).willReturn(startContext);
        given(utteranceRepository.findTop30ByMeetingOrderByIdDesc(meeting)).willReturn(Collections.emptyList());
        given(embeddingClient.embed("what did we decide?")).willReturn(new float[] {0.1f, 0.2f});
        given(decisionRepository.hybridSearch(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("what did we decide?"),
                        org.mockito.ArgumentMatchers.eq(3),
                        org.mockito.ArgumentMatchers.eq(0.7),
                        org.mockito.ArgumentMatchers.eq(0.3)))
                .willReturn(List.of(duplicateDecision, newDecision));
        given(meetingSummaryRepository.hybridSearch(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("what did we decide?"),
                        org.mockito.ArgumentMatchers.eq(3),
                        org.mockito.ArgumentMatchers.eq(0.7),
                        org.mockito.ArgumentMatchers.eq(0.3)))
                .willReturn(List.of(duplicateSummary, newSummary));

        ContextResponse result = contextService.assemble(1L, "what did we decide?");

        assertThat(result.questionContext().decisions())
                .extracting(DecisionSummary::id)
                .containsExactly(2L);
        assertThat(result.questionContext().pastSummaries())
                .extracting(PastSummary::id)
                .containsExactly(12L);
        verify(embeddingClient, times(1)).embed("what did we decide?");
    }

    @Test
    void assemble_questionSearchFails_returnsEmptyQuestionContextWithoutFallbackLookup() {
        Project project = project(10L, "Flodi", "Java");
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingStartContextProvider.getOrCreate(1L)).willReturn(startContext(1L, 10L));
        given(utteranceRepository.findTop30ByMeetingOrderByIdDesc(meeting)).willReturn(Collections.emptyList());
        given(embeddingClient.embed("find summary")).willThrow(new RuntimeException("embedding failed"));

        ContextResponse result = contextService.assemble(1L, "find summary");

        assertThat(result.questionContext().decisions()).isEmpty();
        assertThat(result.questionContext().pastSummaries()).isEmpty();
        verify(decisionRepository, never()).findByProjectIdOrderByIdAsc(10L);
        verify(meetingSummaryRepository, never()).findLatestPastByProjectId(10L, 1L, 5);
    }

    @Test
    void updateContext_savesSummaryAndProcessesEmbedding() {
        Project project = project(1L, "Flodi", "Java");
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        UpdateContextRequest req = new UpdateContextRequest(1L, "summary", null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        contextService.updateContext(1L, req);

        verify(meetingSummaryRepository).save(any(MeetingSummary.class));
        verify(meetingSummaryEmbeddingService).processEmbedding(any(MeetingSummary.class));
    }

    @Test
    void updateContext_savesDecisionsAndWorkLogs() {
        Project project = project(1L, "Flodi", "Java");
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        UpdateContextRequest req = new UpdateContextRequest(
                1L,
                "summary",
                null,
                List.of("decision-1", "decision-2"),
                List.of(
                        new ActionItemRequest("Alice", "write API", null),
                        new ActionItemRequest("Bob", "test API", null)));
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(decisionRepository.save(any(Decision.class))).willAnswer(invocation -> invocation.getArgument(0));

        contextService.updateContext(1L, req);

        verify(decisionRepository, times(2)).save(any(Decision.class));
        verify(decisionEmbeddingService, times(2)).processEmbedding(any(Decision.class));
        verify(workLogRepository, times(2)).save(any(WorkLog.class));
    }

    @Test
    void updateContext_throws_whenMeetingProjectDoesNotMatchRequestedProject() {
        Project requestedProject = project(1L, "A", "Java");
        Project meetingProject = project(2L, "B", "Java");
        Meeting meeting =
                Meeting.builder().project(meetingProject).title("meeting").build();
        UpdateContextRequest req = new UpdateContextRequest(1L, "summary", null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(requestedProject));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> contextService.updateContext(1L, req)).isInstanceOf(ServiceException.class);
    }

    private Project project(Long id, String name, String techStack) {
        Project project = Project.builder().name(name).techStack(techStack).build();
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private Decision decision(Project project, Meeting meeting, Long id, String content) {
        Decision decision = Decision.builder()
                .project(project)
                .meeting(meeting)
                .content(content)
                .build();
        ReflectionTestUtils.setField(decision, "id", id);
        return decision;
    }

    private MeetingSummary meetingSummary(Meeting meeting, Long id, String summary) {
        MeetingSummary meetingSummary =
                MeetingSummary.builder().meeting(meeting).summary(summary).build();
        ReflectionTestUtils.setField(meetingSummary, "id", id);
        return meetingSummary;
    }

    private MeetingStartContext startContext(Long meetingId, Long projectId) {
        return new MeetingStartContext(
                meetingId,
                projectId,
                "Flodi",
                "Java",
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyList());
    }

    private List<Utterance> utterances(Meeting meeting, int start, int end, Integer tokenCount) {
        List<Utterance> utterances = new ArrayList<>();
        for (long i = start; i <= end; i++) {
            utterances.add(utterance(meeting, i, "speaker-" + i, "content-" + i, i, tokenCount));
        }
        return utterances;
    }

    private Utterance utterance(
            Meeting meeting, Long id, String speakerName, String content, long speechOffset, Integer tokenCount) {
        Utterance utterance = Utterance.builder()
                .meeting(meeting)
                .speakerName(speakerName)
                .speakerDiscordId("discord-" + id)
                .content(content)
                .speechStartedAt(LocalDateTime.of(2026, 5, 4, 10, 0).plusSeconds(speechOffset))
                .tokenCount(tokenCount)
                .build();
        ReflectionTestUtils.setField(utterance, "id", id);
        return utterance;
    }
}
