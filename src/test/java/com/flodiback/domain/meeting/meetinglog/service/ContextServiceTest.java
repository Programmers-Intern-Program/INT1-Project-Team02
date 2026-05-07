package com.flodiback.domain.meeting.meetinglog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import com.flodiback.domain.meeting.meeting.entity.ContextCache;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.ContextCacheRepository;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.ActionItemRequest;
import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
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

    @InjectMocks
    private ContextService contextService;

    @BeforeEach
    void setUp() {
        lenient()
                .when(contextCacheRepository.findTopByMeetingOrderByVersionDesc(any(Meeting.class)))
                .thenReturn(Optional.empty());
    }

    // ── assemble() ──────────────────────────────────────────────────────────

    @Test
    void assemble_존재하지않는_회의면_ServiceException() {
        given(meetingRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contextService.assemble(1L, null)).isInstanceOf(ServiceException.class);
    }

    @Test
    void assemble_프로젝트없는_회의면_shortTerm만_채워진_응답() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm()).isNotNull();
        assertThat(result.longTerm().projectName()).isNull();
        assertThat(result.longTerm().decisions()).isEmpty();
    }

    @Test
    void assemble_utterances_id조회후_speechStartedAt_asc_id_asc로_반환() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        Utterance u1 = utterance(meeting, 1L, "Alice", "첫번째", 10, 10);
        Utterance u2 = utterance(meeting, 2L, "Bob", "두번째", 20, 10);
        Utterance u3 = utterance(meeting, 3L, "Carol", "세번째", 20, 10);
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(List.of(u2, u3, u1));

        ContextResponse result = contextService.assemble(1L, null);

        List<String> names = result.shortTerm().recentUtterances().stream()
                .map(us -> us.speakerName())
                .toList();
        assertThat(names).containsExactly("Alice", "Bob", "Carol");
    }

    @Test
    void assemble_noCache_짧은발화가_많으면_최근20개보다_많이_반환() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(utterances(meeting, 1, 30, 10));

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().recentUtterances()).hasSize(30);
        assertThat(result.shortTerm().recentUtterances().get(0).content()).isEqualTo("content-1");
        assertThat(result.shortTerm().recentUtterances().get(29).content()).isEqualTo("content-30");
    }

    @Test
    void assemble_noCache_tokenBudget_초과하면_최근window를_반환() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(utterances(meeting, 1, 30, 100));

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().recentUtterances()).hasSize(20);
        assertThat(result.shortTerm().recentUtterances().get(0).content()).isEqualTo("content-11");
        assertThat(result.shortTerm().recentUtterances().get(19).content()).isEqualTo("content-30");
    }

    @Test
    void assemble_noCache_tokenCount_null이면_content기반으로_추정() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        List<Utterance> utterances = new ArrayList<>();
        for (long i = 1; i <= 30; i++) {
            utterances.add(utterance(meeting, i, "speaker-" + i, "x".repeat(400), i, null));
        }
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(utterances);

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().recentUtterances()).hasSize(20);
        assertThat(result.shortTerm().recentUtterances().get(0).content()).isEqualTo("x".repeat(400));
    }

    @Test
    void assemble_latestContextCache_있으면_idWatermark_이후_tokenBudget_window를_반환() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        ContextCache cache = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("이전까지 인증과 배포를 논의했다.")
                .tokenCount(100)
                .compressedUntilUtteranceId(10L)
                .build();
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(cache));
        given(utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(meeting, 10L))
                .willReturn(utterances(meeting, 11, 31, 10));

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().rollingSummary()).isEqualTo("이전까지 인증과 배포를 논의했다.");
        assertThat(result.shortTerm().recentUtterances()).hasSize(21);
        assertThat(result.shortTerm().recentUtterances().get(0).content()).isEqualTo("content-11");
        assertThat(result.shortTerm().recentUtterances().get(20).content()).isEqualTo("content-31");
    }

    @Test
    void assemble_latestContextCache_tokenBudget_초과해도_최소20개를_반환() {
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        ContextCache cache = ContextCache.builder()
                .meeting(meeting)
                .version(1)
                .compressedText("이전까지 인증과 배포를 논의했다.")
                .tokenCount(100)
                .compressedUntilUtteranceId(10L)
                .build();
        given(contextCacheRepository.findTopByMeetingOrderByVersionDesc(meeting))
                .willReturn(Optional.of(cache));
        given(utteranceRepository.findByMeetingAndIdGreaterThanOrderByIdAsc(meeting, 10L))
                .willReturn(utterances(meeting, 11, 31, 200));

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm().recentUtterances()).hasSize(20);
        assertThat(result.shortTerm().recentUtterances().get(0).content()).isEqualTo("content-12");
        assertThat(result.shortTerm().recentUtterances().get(19).content()).isEqualTo("content-31");
    }

    @Test
    void assemble_프로젝트있으면_longTerm_포함() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(10L);
        given(project.getName()).willReturn("테스트 프로젝트");
        given(project.getTechStack()).willReturn("Java, Spring");
        given(project.getMetadata()).willReturn(null);

        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(project);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(Collections.emptyList());
        given(decisionRepository.findByProjectIdOrderByIdAsc(10L)).willReturn(Collections.emptyList());
        given(meetingSummaryRepository.findLatestPastByProjectId(10L, 1L, 5)).willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.longTerm().projectName()).isEqualTo("테스트 프로젝트");
        assertThat(result.longTerm().techStack()).isEqualTo("Java, Spring");
    }

    @Test
    void assemble_question있으면_MeetingSummary_hybridSearch_사용() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(10L);
        given(project.getName()).willReturn("테스트 프로젝트");
        given(project.getTechStack()).willReturn("Java, Spring");
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(project);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(Collections.emptyList());
        given(embeddingClient.embed("이전 결정 뭐였지?")).willReturn(new float[] {0.1f, 0.2f});
        given(decisionRepository.hybridSearch(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("이전 결정 뭐였지?"),
                        org.mockito.ArgumentMatchers.eq(5),
                        org.mockito.ArgumentMatchers.eq(0.7),
                        org.mockito.ArgumentMatchers.eq(0.3)))
                .willReturn(Collections.emptyList());
        given(meetingSummaryRepository.hybridSearch(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("이전 결정 뭐였지?"),
                        org.mockito.ArgumentMatchers.eq(5),
                        org.mockito.ArgumentMatchers.eq(0.7),
                        org.mockito.ArgumentMatchers.eq(0.3)))
                .willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, "이전 결정 뭐였지?");

        assertThat(result.longTerm().pastSummaries()).isEmpty();
        verify(meetingSummaryRepository)
                .hybridSearch(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("이전 결정 뭐였지?"),
                        org.mockito.ArgumentMatchers.eq(5),
                        org.mockito.ArgumentMatchers.eq(0.7),
                        org.mockito.ArgumentMatchers.eq(0.3));
    }

    @Test
    void assemble_summaryHybridSearch_실패하면_latestPastSummaries로_fallback() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(10L);
        given(project.getName()).willReturn("테스트 프로젝트");
        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(project);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(utteranceRepository.findByMeetingOrderByIdAsc(meeting)).willReturn(Collections.emptyList());
        given(embeddingClient.embed("요약 찾아줘")).willReturn(new float[] {0.1f});
        given(decisionRepository.hybridSearch(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("요약 찾아줘"),
                        org.mockito.ArgumentMatchers.eq(5),
                        org.mockito.ArgumentMatchers.eq(0.7),
                        org.mockito.ArgumentMatchers.eq(0.3)))
                .willReturn(Collections.emptyList());
        given(meetingSummaryRepository.hybridSearch(
                        org.mockito.ArgumentMatchers.eq(10L),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("요약 찾아줘"),
                        org.mockito.ArgumentMatchers.eq(5),
                        org.mockito.ArgumentMatchers.eq(0.7),
                        org.mockito.ArgumentMatchers.eq(0.3)))
                .willThrow(new RuntimeException("search failed"));
        given(meetingSummaryRepository.findLatestPastByProjectId(10L, 1L, 5)).willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, "요약 찾아줘");

        assertThat(result.longTerm().pastSummaries()).isEmpty();
        verify(meetingSummaryRepository).findLatestPastByProjectId(10L, 1L, 5);
    }

    // ── updateContext() ──────────────────────────────────────────────────────

    @Test
    void updateContext_존재하지않는_프로젝트면_ServiceException() {
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, null);
        given(projectRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contextService.updateContext(99L, req)).isInstanceOf(ServiceException.class);
    }

    @Test
    void updateContext_존재하지않는_회의면_ServiceException() {
        Project project = mock(Project.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contextService.updateContext(1L, req)).isInstanceOf(ServiceException.class);
    }

    @Test
    void updateContext_MeetingSummary는_항상_저장() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "회의 요약", null, null, null);
        given(project.getId()).willReturn(1L);
        given(meeting.getProject()).willReturn(project);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        contextService.updateContext(1L, req);

        verify(meetingSummaryRepository).save(any(MeetingSummary.class));
        verify(meetingSummaryEmbeddingService).processEmbedding(any(MeetingSummary.class));
    }

    @Test
    void updateContext_decisions_null이면_Decision_저장안함() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, null);
        given(project.getId()).willReturn(1L);
        given(meeting.getProject()).willReturn(project);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        contextService.updateContext(1L, req);

        verify(decisionRepository, never()).save(any());
    }

    @Test
    void updateContext_decisions_2개면_Decision_2번_저장() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, List.of("결정1", "결정2"), null);
        given(project.getId()).willReturn(1L);
        given(meeting.getProject()).willReturn(project);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        contextService.updateContext(1L, req);

        verify(decisionRepository, times(2)).save(any(Decision.class));
    }

    @Test
    void updateContext_actionItems_null이면_WorkLog_저장안함() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, null);
        given(project.getId()).willReturn(1L);
        given(meeting.getProject()).willReturn(project);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        contextService.updateContext(1L, req);

        verify(workLogRepository, never()).save(any());
    }

    @Test
    void updateContext_actionItems_2개면_WorkLog_2번_저장() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        ActionItemRequest item1 = new ActionItemRequest("김철수", "API 작성", null);
        ActionItemRequest item2 = new ActionItemRequest("이영희", "테스트 작성", null);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, List.of(item1, item2));
        given(project.getId()).willReturn(1L);
        given(meeting.getProject()).willReturn(project);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        contextService.updateContext(1L, req);

        verify(workLogRepository, times(2)).save(any(WorkLog.class));
    }

    @Test
    void updateContext_다른프로젝트의_회의면_ServiceException() {
        Project requestedProject = mock(Project.class);
        Project meetingProject = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, null);
        given(meetingProject.getId()).willReturn(2L);
        given(meeting.getProject()).willReturn(meetingProject);
        given(projectRepository.findById(1L)).willReturn(Optional.of(requestedProject));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> contextService.updateContext(1L, req)).isInstanceOf(ServiceException.class);
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
