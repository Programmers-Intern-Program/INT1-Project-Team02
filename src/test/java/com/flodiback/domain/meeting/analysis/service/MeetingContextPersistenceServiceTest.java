package com.flodiback.domain.meeting.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.decision.decision.service.DecisionEmbeddingService;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.dto.ActionItemRequest;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.domain.meeting.meetinglog.service.MeetingSummaryEmbeddingService;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.project.worklog.entity.WorkLog;
import com.flodiback.domain.project.worklog.event.WorkLogChangedEvent;
import com.flodiback.domain.project.worklog.repository.WorkLogRepository;
import com.flodiback.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class MeetingContextPersistenceServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingSummaryRepository meetingSummaryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkLogRepository workLogRepository;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private DecisionEmbeddingService decisionEmbeddingService;

    @Mock
    private MeetingSummaryEmbeddingService meetingSummaryEmbeddingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private MeetingContextPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new MeetingContextPersistenceService(
                meetingRepository,
                meetingSummaryRepository,
                projectRepository,
                workLogRepository,
                decisionRepository,
                decisionEmbeddingService,
                meetingSummaryEmbeddingService,
                eventPublisher,
                transactionManager);
    }

    @Test
    void saveSummaryRequired_savesOnlySummaryAndReturnsSavedEntity() {
        Project project = project(1L);
        Meeting meeting = meeting(project);
        UpdateContextRequest req = new UpdateContextRequest(10L, "summary", "open", null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(meetingSummaryRepository.save(any(MeetingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        MeetingSummary saved = service.saveSummaryRequired(1L, req);

        assertThat(saved.getSummary()).isEqualTo("summary");
        assertThat(saved.getUnresolvedItems()).isEqualTo("open");
        verify(meetingSummaryRepository).save(any(MeetingSummary.class));
        verify(decisionRepository, never()).save(any());
        verify(workLogRepository, never()).save(any());
    }

    @Test
    void saveSummaryEmbeddingBestEffort_swallowsEmbeddingFailure() {
        MeetingSummary summary = MeetingSummary.builder()
                .meeting(meeting(project(1L)))
                .summary("summary")
                .build();
        doThrow(new RuntimeException("embedding failed"))
                .when(meetingSummaryEmbeddingService)
                .processEmbedding(summary);

        assertThatCode(() -> service.saveSummaryEmbeddingBestEffort(summary)).doesNotThrowAnyException();
    }

    @Test
    void saveDerivedItemsBestEffort_savesDecisionsAndWorkLogs() {
        Project project = project(1L);
        Meeting meeting = meeting(project);
        UpdateContextRequest req = new UpdateContextRequest(
                10L,
                "summary",
                null,
                List.of("decision-1", "decision-2"),
                List.of(
                        new ActionItemRequest("discord-alice", "Alice", "write API", null),
                        new ActionItemRequest("Bob", "test API", null)),
                null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(decisionRepository.save(any(Decision.class))).willAnswer(invocation -> invocation.getArgument(0));
        stubNewTransaction();

        service.saveDerivedItemsBestEffort(1L, req);

        ArgumentCaptor<TransactionDefinition> transactionDefinitionCaptor =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(transactionDefinitionCaptor.capture());
        assertThat(transactionDefinitionCaptor.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(decisionRepository, times(2)).save(any(Decision.class));
        verify(decisionEmbeddingService, times(2)).processEmbedding(any(Decision.class));
        ArgumentCaptor<WorkLog> workLogCaptor = ArgumentCaptor.forClass(WorkLog.class);
        verify(workLogRepository, times(2)).save(workLogCaptor.capture());
        assertThat(workLogCaptor.getAllValues())
                .extracting(WorkLog::getAssigneeName)
                .containsExactly("Alice", "Bob");
        assertThat(workLogCaptor.getAllValues())
                .extracting(WorkLog::getAssigneeDiscordId)
                .containsExactly("discord-alice", null);
        verify(eventPublisher).publishEvent(new WorkLogChangedEvent(1L, 10L));
    }

    @Test
    void saveDerivedItemsBestEffort_updatesExistingWorkLogStatusInProject() {
        Project project = project(1L);
        Meeting meeting = meeting(project);
        WorkLog workLog = workLog(project, meeting, 7L);
        UpdateContextRequest req = new UpdateContextRequest(
                10L, "summary", null, null, null, List.of(new UpdateContextRequest.WorkLogStatusUpdate(7L, "done")));
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(workLogRepository.findByIdAndProjectId(7L, 1L)).willReturn(Optional.of(workLog));
        stubNewTransaction();

        service.saveDerivedItemsBestEffort(1L, req);

        assertThat(workLog.getStatus()).isEqualTo("DONE");
        verify(workLogRepository).save(workLog);
        verify(eventPublisher).publishEvent(new WorkLogChangedEvent(1L, 10L));
    }

    @Test
    void saveDerivedItemsBestEffort_doesNotPublishWorkLogEventWithoutWorkLogChanges() {
        Project project = project(1L);
        Meeting meeting = meeting(project);
        UpdateContextRequest req = new UpdateContextRequest(10L, "summary", null, List.of("decision"), null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(decisionRepository.save(any(Decision.class))).willAnswer(invocation -> invocation.getArgument(0));
        stubNewTransaction();

        service.saveDerivedItemsBestEffort(1L, req);

        verify(eventPublisher, never()).publishEvent(any(WorkLogChangedEvent.class));
    }

    @Test
    void saveDerivedItemsBestEffort_swallowsWorkLogFailure() {
        Project project = project(1L);
        Meeting meeting = meeting(project);
        UpdateContextRequest req = new UpdateContextRequest(
                10L, "summary", null, null, List.of(new ActionItemRequest("Alice", "write API", null)), null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(workLogRepository.save(any(WorkLog.class))).willThrow(new RuntimeException("worklog failed"));
        stubNewTransaction();

        assertThatCode(() -> service.saveDerivedItemsBestEffort(1L, req)).doesNotThrowAnyException();
    }

    @Test
    void saveDerivedItemsBestEffort_swallowsTransactionFailure() {
        Project project = project(1L);
        Meeting meeting = meeting(project);
        UpdateContextRequest req = new UpdateContextRequest(
                10L, "summary", null, null, List.of(new ActionItemRequest("Alice", "write API", null)), null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        stubNewTransaction();
        doThrow(new RuntimeException("commit failed")).when(transactionManager).commit(any());

        assertThatCode(() -> service.saveDerivedItemsBestEffort(1L, req)).doesNotThrowAnyException();
    }

    @Test
    void saveSummaryRequired_throwsWhenMeetingProjectDoesNotMatch() {
        Project requestedProject = project(1L);
        Project otherProject = project(2L);
        Meeting meeting = meeting(otherProject);
        UpdateContextRequest req = new UpdateContextRequest(10L, "summary", null, null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(requestedProject));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> service.saveSummaryRequired(1L, req)).isInstanceOf(ServiceException.class);
    }

    @Test
    void saveSummaryRequired_throwsWhenMeetingDoesNotExist() {
        UpdateContextRequest req = new UpdateContextRequest(10L, "summary", null, null, null, null);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project(1L)));
        given(meetingRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveSummaryRequired(1L, req)).isInstanceOf(ServiceException.class);
    }

    private Project project(Long id) {
        Project project = Project.builder().name("project").build();
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private Meeting meeting(Project project) {
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        ReflectionTestUtils.setField(meeting, "id", 10L);
        return meeting;
    }

    private WorkLog workLog(Project project, Meeting meeting, Long id) {
        WorkLog workLog = WorkLog.builder()
                .project(project)
                .meeting(meeting)
                .assigneeName("Alice")
                .task("write API")
                .dueDate(null)
                .build();
        ReflectionTestUtils.setField(workLog, "id", id);
        return workLog;
    }

    private void stubNewTransaction() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
    }
}
