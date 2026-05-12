package com.flodiback.domain.meeting.meeting.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meeting.dto.ActiveMeetingResponse;
import com.flodiback.domain.meeting.meeting.dto.CreateMeetingRequest;
import com.flodiback.domain.meeting.meeting.dto.CreateMeetingResponse;
import com.flodiback.domain.meeting.meeting.dto.MeetingDetailResponse;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.event.MeetingEndedEvent;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.global.enums.MeetingStatus;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CreateMeetingResponse create(CreateMeetingRequest req) {
        Project project = null;
        if (req.projectId() != null) {
            project = projectRepository
                    .findById(req.projectId())
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        }

        Meeting meeting = Meeting.builder().project(project).title(req.title()).build();
        meetingRepository.save(meeting);

        return new CreateMeetingResponse(
                meeting.getId(),
                project != null ? project.getId() : null,
                meeting.getTitle(),
                meeting.getStartedAt(),
                meeting.getStatus(),
                project != null);
    }

    public MeetingDetailResponse end(Long id, String requesterId) {
        Meeting meeting =
                meetingRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));
        checkWritePermission(meeting, requesterId);

        meeting.end();
        meetingRepository.save(meeting);

        eventPublisher.publishEvent(new MeetingEndedEvent(meeting.getId()));
        return toDetailResponse(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getById(Long id, String guildId) {
        Meeting meeting =
                meetingRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));
        checkReadPermission(meeting, guildId);
        return toDetailResponse(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getByIdForUser(Long id, List<String> guildIds) {
        Meeting meeting =
                meetingRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));
        Project project = meeting.getProject();
        if (project != null
                && project.getServer() != null
                && !guildIds.contains(project.getServer().getGuildId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }
        return toDetailResponse(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingDetailResponse> getByProjectId(Long projectId, String guildId) {
        Project project =
                projectRepository.findById(projectId).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkReadPermission(project, guildId);
        return meetingRepository.findByProjectId(projectId).stream()
                .map(this::toDetailResponse)
                .toList();
    }

    public void delete(Long id, String requesterId) {
        Meeting meeting =
                meetingRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));
        checkWritePermission(meeting, requesterId);
        meetingRepository.delete(meeting);
    }

    @Transactional(readOnly = true)
    public Optional<ActiveMeetingResponse> getActiveMeeting(String channelId) {
        return projectRepository
                .findByChannelId(channelId)
                .flatMap(project -> meetingRepository.findFirstByProjectAndStatusOrderByStartedAtDesc(
                        project, MeetingStatus.IN_PROGRESS))
                .map(meeting -> new ActiveMeetingResponse(meeting.getId(), meeting.getTitle()));
    }

    private void checkReadPermission(Meeting meeting, String guildId) {
        checkReadPermission(meeting.getProject(), guildId);
    }

    private void checkReadPermission(Project project, String guildId) {
        if (guildId == null || project == null || project.getServer() == null) return;
        if (!guildId.equals(project.getServer().getGuildId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }
    }

    private void checkWritePermission(Meeting meeting, String requesterId) {
        Project project = meeting.getProject();
        if (requesterId == null || project == null || project.getOwnerDiscordId() == null) return;
        if (!requesterId.equals(project.getOwnerDiscordId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }
    }

    private MeetingDetailResponse toDetailResponse(Meeting meeting) {
        return new MeetingDetailResponse(
                meeting.getId(),
                meeting.getProject() != null ? meeting.getProject().getId() : null,
                meeting.getTitle(),
                meeting.getStartedAt(),
                meeting.getEndedAt(),
                meeting.getStatus());
    }
}
