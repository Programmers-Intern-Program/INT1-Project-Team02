package com.flodiback.domain.project.project.service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meeting.entity.Meeting;
import com.flodiback.domain.meeting.meeting.repository.MeetingRepository;
import com.flodiback.domain.project.project.dto.CreateProjectRequest;
import com.flodiback.domain.project.project.dto.ProjectResponse;
import com.flodiback.domain.project.project.dto.UpdateProjectRequest;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.event.ProjectCreatedEvent;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.server.server.entity.DiscordServer;
import com.flodiback.domain.server.server.repository.DiscordServerRepository;
import com.flodiback.global.enums.MeetingStatus;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DiscordServerRepository discordServerRepository;
    private final MeetingRepository meetingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectResponse create(CreateProjectRequest req) {
        if (req.channelId() != null) {
            projectRepository.findByChannelId(req.channelId()).ifPresent(p -> {
                throw new ServiceException("400-2", "이 채널에는 이미 프로젝트가 연결되어 있습니다.");
            });
        }

        DiscordServer server = null;
        if (req.guildId() != null) {
            server = discordServerRepository
                    .findByGuildId(req.guildId())
                    .orElseGet(() -> discordServerRepository.save(DiscordServer.builder()
                            .guildId(req.guildId())
                            .guildName(req.guildName() != null ? req.guildName() : req.guildId())
                            .build()));
        } else if (req.serverId() != null) {
            server = discordServerRepository
                    .findById(req.serverId())
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 서버입니다."));
        }

        Project project = Project.builder()
                .server(server)
                .channelId(req.channelId())
                .ownerDiscordId(req.ownerDiscordId())
                .name(req.name())
                .description(req.description())
                .techStack(req.techStack())
                .build();
        projectRepository.save(project);
        eventPublisher.publishEvent(
                new ProjectCreatedEvent(project.getId(), server != null ? server.getGuildId() : null));

        return toResponse(project, null);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAll() {
        List<Project> projects = projectRepository.findAll();
        Map<Long, Long> activeMeetingIdMap = buildActiveMeetingIdMap(projects);
        return projects.stream()
                .map(p -> toResponse(p, activeMeetingIdMap.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getByGuildIds(List<String> guildIds) {
        List<Project> projects = projectRepository.findByServerGuildIdIn(guildIds);
        Map<Long, Long> activeMeetingIdMap = buildActiveMeetingIdMap(projects);
        return projects.stream()
                .map(p -> toResponse(p, activeMeetingIdMap.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByIdForUser(Long id, List<String> guildIds) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        if (project.getServer() != null
                && !guildIds.contains(project.getServer().getGuildId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }
        return toResponseWithActiveMeeting(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByChannelIdForUser(String channelId, List<String> guildIds) {
        Project project = projectRepository
                .findByChannelId(channelId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        if (project.getServer() != null
                && !guildIds.contains(project.getServer().getGuildId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }
        return toResponseWithActiveMeeting(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByChannelId(String channelId, String guildId) {
        Project project = projectRepository
                .findByChannelId(channelId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkReadPermission(project, guildId);
        return toResponseWithActiveMeeting(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id, String guildId) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkReadPermission(project, guildId);
        return toResponseWithActiveMeeting(project);
    }

    public ProjectResponse update(Long id, UpdateProjectRequest req, String requesterId) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkWritePermission(project, requesterId);
        project.update(req.name(), req.description(), req.techStack());
        return toResponseWithActiveMeeting(project);
    }

    public void connectChannel(Long id, String channelId, String requesterId) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkWritePermission(project, requesterId);
        project.connectChannel(channelId);
    }

    public void disconnectChannel(Long id, String requesterId) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkWritePermission(project, requesterId);
        project.disconnectChannel();
    }

    public void delete(Long id, String requesterId) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkWritePermission(project, requesterId);
        project.softDelete();
    }

    private void checkReadPermission(Project project, String guildId) {
        if (guildId == null || project.getServer() == null) return;
        if (!guildId.equals(project.getServer().getGuildId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }
    }

    private void checkWritePermission(Project project, String requesterId) {
        if (requesterId == null || project.getOwnerDiscordId() == null) return;
        if (!requesterId.equals(project.getOwnerDiscordId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }
    }

    private Map<Long, Long> buildActiveMeetingIdMap(List<Project> projects) {
        if (projects.isEmpty()) return Map.of();
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        return meetingRepository.findActiveByProjectIds(projectIds, MeetingStatus.IN_PROGRESS).stream()
                .collect(Collectors.toMap(
                        m -> m.getProject().getId(), Meeting::getId, (first, duplicate) -> first)); // 먼저 삽입된 값(최신)을 유지
    }

    private ProjectResponse toResponseWithActiveMeeting(Project project) {
        Long activeMeetingId = meetingRepository
                .findFirstByProjectAndStatusOrderByStartedAtDesc(project, MeetingStatus.IN_PROGRESS)
                .map(Meeting::getId)
                .orElse(null);
        return toResponse(project, activeMeetingId);
    }

    private ProjectResponse toResponse(Project project, Long activeMeetingId) {
        return new ProjectResponse(
                project.getId(),
                project.getServer() != null ? project.getServer().getId() : null,
                project.getName(),
                project.getDescription(),
                project.getTechStack(),
                project.getCreatedAt(),
                project.getChannelId(),
                activeMeetingId);
    }
}
