package com.flodiback.domain.project.project.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.project.project.dto.CreateProjectRequest;
import com.flodiback.domain.project.project.dto.ProjectResponse;
import com.flodiback.domain.project.project.dto.UpdateProjectRequest;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.server.server.entity.DiscordServer;
import com.flodiback.domain.server.server.repository.DiscordServerRepository;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DiscordServerRepository discordServerRepository;

    public ProjectResponse create(CreateProjectRequest req) {
        if (req.channelId() != null) {
            projectRepository.findByChannelId(req.channelId()).ifPresent(p -> {
                throw new ServiceException("400-2", "이 채널에는 이미 프로젝트가 연결되어 있습니다.");
            });
        }

        DiscordServer server = null;
        if (req.serverId() != null) {
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

        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAll() {
        return projectRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getByGuildIds(List<String> guildIds) {
        return projectRepository.findByServerGuildIdIn(guildIds).stream()
                .map(this::toResponse)
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
        return toResponse(project);
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
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByChannelId(String channelId, String guildId) {
        Project project = projectRepository
                .findByChannelId(channelId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkReadPermission(project, guildId);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id, String guildId) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkReadPermission(project, guildId);
        return toResponse(project);
    }

    public ProjectResponse update(Long id, UpdateProjectRequest req, String requesterId) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        checkWritePermission(project, requesterId);
        project.update(req.name(), req.description(), req.techStack());
        return toResponse(project);
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

    ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getServer() != null ? project.getServer().getId() : null,
                project.getName(),
                project.getDescription(),
                project.getTechStack(),
                project.getCreatedAt());
    }
}
