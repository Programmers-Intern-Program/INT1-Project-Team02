package com.flodiback.domain.meeting.meeting.service;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meeting.dto.CreateMeetingRequest;
import com.flodiback.domain.meeting.meeting.dto.CreateMeetingResponse;
import com.flodiback.domain.meeting.meeting.dto.MeetingDetailResponse;
import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final ProjectRepository projectRepository;

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
                meeting.getStatus());
    }

    public MeetingDetailResponse end(Long id) {
        Meeting meeting =
                meetingRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));

        meeting.end();
        meetingRepository.save(meeting);

        return toDetailResponse(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingDetailResponse getById(Long id) {
        Meeting meeting =
                meetingRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 회의입니다."));

        return toDetailResponse(meeting);
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
