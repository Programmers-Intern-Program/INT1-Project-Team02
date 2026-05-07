package com.flodiback.domain.project.project.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.project.project.dto.CreateProjectRequest;
import com.flodiback.domain.project.project.dto.ProjectResponse;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.server.server.entity.DiscordServer;
import com.flodiback.domain.server.server.repository.DiscordServerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DiscordServerRepository discordServerRepository;

    public ProjectResponse create(CreateProjectRequest req) {
        DiscordServer server = null;
        if (req.serverId() != null) {
            server = discordServerRepository
                    .findById(req.serverId())
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 서버입니다."));
        }

        Project project = Project.builder()
                .server(server)
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
    public ProjectResponse getById(Long id) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        return toResponse(project);
    }

    public void delete(Long id) {
        Project project =
                projectRepository.findById(id).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));
        project.softDelete();
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
