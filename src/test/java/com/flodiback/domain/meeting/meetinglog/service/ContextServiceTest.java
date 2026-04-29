package com.flodiback.domain.meeting.meetinglog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.decision.decision.service.DecisionEmbeddingService;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
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

    @InjectMocks
    private ContextService contextService;

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
        given(utteranceRepository.findTop20ByMeetingIdOrderBySpokenAtDesc(1L)).willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.shortTerm()).isNotNull();
        assertThat(result.longTerm().projectName()).isNull();
        assertThat(result.longTerm().decisions()).isEmpty();
    }

    @Test
    void assemble_utterances_DESC로_가져와서_ASC_시간순으로_반환() {
        Utterance u1 = mock(Utterance.class);
        Utterance u2 = mock(Utterance.class);
        Utterance u3 = mock(Utterance.class);
        given(u1.getSpeakerName()).willReturn("Alice");
        given(u1.getContent()).willReturn("첫번째");
        given(u1.getSpokenAt()).willReturn(null);
        given(u2.getSpeakerName()).willReturn("Bob");
        given(u2.getContent()).willReturn("두번째");
        given(u2.getSpokenAt()).willReturn(null);
        given(u3.getSpeakerName()).willReturn("Carol");
        given(u3.getContent()).willReturn("세번째");
        given(u3.getSpokenAt()).willReturn(null);

        Meeting meeting = mock(Meeting.class);
        given(meeting.getProject()).willReturn(null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        // DB는 DESC(최신→과거) 순으로 반환: [Carol, Bob, Alice]
        given(utteranceRepository.findTop20ByMeetingIdOrderBySpokenAtDesc(1L))
                .willReturn(new ArrayList<>(List.of(u3, u2, u1)));

        ContextResponse result = contextService.assemble(1L, null);

        // reverse() 후 ASC(과거→최신): [Alice, Bob, Carol]
        List<String> names = result.shortTerm().recentUtterances().stream()
                .map(us -> us.speakerName())
                .toList();
        assertThat(names).containsExactly("Alice", "Bob", "Carol");
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
        given(utteranceRepository.findTop20ByMeetingIdOrderBySpokenAtDesc(1L)).willReturn(Collections.emptyList());
        given(decisionRepository.findByProjectIdOrderByIdAsc(10L)).willReturn(Collections.emptyList());
        given(meetingSummaryRepository.findPastByProjectId(10L, 1L)).willReturn(Collections.emptyList());

        ContextResponse result = contextService.assemble(1L, null);

        assertThat(result.longTerm().projectName()).isEqualTo("테스트 프로젝트");
        assertThat(result.longTerm().techStack()).isEqualTo("Java, Spring");
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
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        contextService.updateContext(1L, req);

        verify(meetingSummaryRepository).save(any(MeetingSummary.class));
    }

    @Test
    void updateContext_decisions_null이면_Decision_저장안함() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        contextService.updateContext(1L, req);

        verify(decisionRepository, never()).save(any());
    }

    @Test
    void updateContext_decisions_2개면_Decision_2번_저장() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, List.of("결정1", "결정2"), null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        contextService.updateContext(1L, req);

        verify(decisionRepository, times(2)).save(any(Decision.class));
    }

    @Test
    void updateContext_actionItems_null이면_WorkLog_저장안함() {
        Project project = mock(Project.class);
        Meeting meeting = mock(Meeting.class);
        UpdateContextRequest req = new UpdateContextRequest(1L, "요약", null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

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
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        contextService.updateContext(1L, req);

        verify(workLogRepository, times(2)).save(any(WorkLog.class));
    }
}
