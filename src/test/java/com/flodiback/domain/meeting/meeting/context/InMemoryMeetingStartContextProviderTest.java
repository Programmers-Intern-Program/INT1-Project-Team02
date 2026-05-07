package com.flodiback.domain.meeting.meeting.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.repository.MeetingSummaryRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.worklog.entity.WorkLog;
import com.flodiback.domain.project.worklog.repository.WorkLogRepository;

@ExtendWith(MockitoExtension.class)
class InMemoryMeetingStartContextProviderTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private DecisionRepository decisionRepository;

    @Mock
    private MeetingSummaryRepository meetingSummaryRepository;

    @Mock
    private WorkLogRepository workLogRepository;

    @Test
    void getOrCreate_reusesCachedContext_untilInvalidated() {
        InMemoryMeetingStartContextProvider provider = provider();
        Project project = project(10L);
        Meeting meeting = meeting(1L, project);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(decisionRepository.findTop5ByProjectIdOrderByIdDesc(10L))
                .willReturn(List.of(decision(project, meeting, 1L)));
        given(meetingSummaryRepository.findLatestPastByProjectId(10L, 1L, 3))
                .willReturn(List.of(meetingSummary(meeting, 11L, "summary", null)));
        given(meetingSummaryRepository.findLatestUnresolvedItemsByProjectId(10L, 1L))
                .willReturn(Optional.of("open issue"));
        given(workLogRepository.findTop5ByProjectIdAndStatusOrderByIdDesc(10L, "TODO"))
                .willReturn(List.of(workLog(project, meeting, 21L)));

        MeetingStartContext first = provider.getOrCreate(1L);
        MeetingStartContext second = provider.getOrCreate(1L);
        provider.invalidate(1L);
        MeetingStartContext third = provider.getOrCreate(1L);

        assertThat(first).isSameAs(second);
        assertThat(third).isNotSameAs(first);
        assertThat(first.recentDecisions()).hasSize(1);
        assertThat(first.recentSummaries()).hasSize(1);
        assertThat(first.unresolvedItems()).isEqualTo("open issue");
        assertThat(first.activeWorkLogs()).hasSize(1);
        verify(meetingRepository, times(2)).findById(1L);
    }

    @Test
    void getOrCreate_returnsNoProjectContext_whenMeetingHasNoProject() {
        InMemoryMeetingStartContextProvider provider = provider();
        Meeting meeting = meeting(1L, null);
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        MeetingStartContext result = provider.getOrCreate(1L);

        assertThat(result.meetingId()).isEqualTo(1L);
        assertThat(result.projectId()).isNull();
        assertThat(result.recentDecisions()).isEmpty();
        assertThat(result.activeWorkLogs()).isEmpty();
    }

    private InMemoryMeetingStartContextProvider provider() {
        return new InMemoryMeetingStartContextProvider(
                meetingRepository, decisionRepository, meetingSummaryRepository, workLogRepository);
    }

    private Project project(Long id) {
        Project project = Project.builder().name("Flodi").techStack("Java").build();
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private Meeting meeting(Long id, Project project) {
        Meeting meeting = Meeting.builder().project(project).title("meeting").build();
        ReflectionTestUtils.setField(meeting, "id", id);
        return meeting;
    }

    private Decision decision(Project project, Meeting meeting, Long id) {
        Decision decision = Decision.builder()
                .project(project)
                .meeting(meeting)
                .content("decision-" + id)
                .build();
        ReflectionTestUtils.setField(decision, "id", id);
        return decision;
    }

    private MeetingSummary meetingSummary(Meeting meeting, Long id, String summary, String unresolvedItems) {
        MeetingSummary meetingSummary = MeetingSummary.builder()
                .meeting(meeting)
                .summary(summary)
                .unresolvedItems(unresolvedItems)
                .build();
        ReflectionTestUtils.setField(meetingSummary, "id", id);
        return meetingSummary;
    }

    private WorkLog workLog(Project project, Meeting meeting, Long id) {
        WorkLog workLog = WorkLog.builder()
                .project(project)
                .meeting(meeting)
                .assigneeName("Alice")
                .task("write API")
                .build();
        ReflectionTestUtils.setField(workLog, "id", id);
        return workLog;
    }
}
