package com.flodiback.domain.meeting.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.domain.meeting.meeting.dto.CreateMeetingRequest;
import com.flodiback.domain.meeting.meeting.dto.CreateMeetingResponse;
import com.flodiback.domain.meeting.meeting.dto.MeetingDetailResponse;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.global.enums.MeetingStatus;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @InjectMocks
    private MeetingService meetingService;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private ProjectRepository projectRepository;

    // ─── create ───────────────────────────────────────────

    @Test
    void create_projectId없으면_project_null로_미팅생성() {
        // given
        CreateMeetingRequest req = new CreateMeetingRequest(null, "테스트 회의");
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        CreateMeetingResponse response = meetingService.create(req);

        // then
        verify(projectRepository, never()).findById(any());
        verify(meetingRepository).save(any(Meeting.class));
        assertThat(response.projectId()).isNull();
        assertThat(response.title()).isEqualTo("테스트 회의");
        assertThat(response.status()).isEqualTo(MeetingStatus.IN_PROGRESS);
    }

    @Test
    void create_유효한_projectId면_미팅생성() {
        // given
        Project project = Project.builder().name("테스트 프로젝트").build();
        CreateMeetingRequest req = new CreateMeetingRequest(1L, "테스트 회의");
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        CreateMeetingResponse response = meetingService.create(req);

        // then
        verify(projectRepository).findById(1L);
        verify(meetingRepository).save(any(Meeting.class));
        assertThat(response.title()).isEqualTo("테스트 회의");
        assertThat(response.status()).isEqualTo(MeetingStatus.IN_PROGRESS);
    }

    @Test
    void create_존재하지않는_projectId면_예외발생() {
        // given
        CreateMeetingRequest req = new CreateMeetingRequest(999L, "테스트 회의");
        given(projectRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> meetingService.create(req))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 프로젝트입니다.");

        verify(meetingRepository, never()).save(any());
    }

    // ─── end ──────────────────────────────────────────────

    @Test
    void end_유효한_id면_회의종료() {
        // given
        Meeting meeting = Meeting.builder().title("테스트 회의").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        MeetingDetailResponse response = meetingService.end(1L);

        // then
        verify(meetingRepository).save(meeting);
        assertThat(response.status()).isEqualTo(MeetingStatus.COMPLETED);
        assertThat(response.endedAt()).isNotNull();
    }

    @Test
    void end_이미_종료된_회의도_덮어쓰기() {
        // given
        Meeting meeting = Meeting.builder().title("테스트 회의").build();
        meeting.end(); // 미리 종료
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        MeetingDetailResponse response = meetingService.end(1L);

        // then
        verify(meetingRepository).save(meeting);
        assertThat(response.status()).isEqualTo(MeetingStatus.COMPLETED);
    }

    @Test
    void end_존재하지않는_id면_예외발생() {
        // given
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> meetingService.end(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 회의입니다.");

        verify(meetingRepository, never()).save(any());
    }

    // ─── getById ──────────────────────────────────────────

    @Test
    void getById_유효한_id면_회의반환() {
        // given
        Meeting meeting = Meeting.builder().title("테스트 회의").build();
        given(meetingRepository.findById(1L)).willReturn(Optional.of(meeting));

        // when
        MeetingDetailResponse response = meetingService.getById(1L);

        // then
        assertThat(response.title()).isEqualTo("테스트 회의");
        assertThat(response.status()).isEqualTo(MeetingStatus.IN_PROGRESS);
    }

    @Test
    void getById_존재하지않는_id면_예외발생() {
        // given
        given(meetingRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> meetingService.getById(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 회의입니다.");
    }
}
