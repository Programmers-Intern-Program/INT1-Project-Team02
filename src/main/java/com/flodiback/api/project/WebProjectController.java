package com.flodiback.api.project;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.project.project.dto.ProjectResponse;
import com.flodiback.domain.project.project.service.ProjectService;
import com.flodiback.global.rsData.RsData;
import com.flodiback.global.util.SecurityContextUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class WebProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<RsData<List<ProjectResponse>>> getProjects() {
        List<String> guildIds = SecurityContextUtil.getGuildIds();
        List<ProjectResponse> projects = projectService.getByGuildIds(guildIds);
        return ResponseEntity.ok(RsData.of("200-1", "프로젝트 목록 조회 성공.", projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RsData<ProjectResponse>> getProject(@PathVariable Long id) {
        List<String> guildIds = SecurityContextUtil.getGuildIds();
        ProjectResponse project = projectService.getByIdForUser(id, guildIds);
        return ResponseEntity.ok(RsData.of("200-1", "프로젝트 조회 성공.", project));
    }

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<RsData<ProjectResponse>> getProjectByChannel(@PathVariable String channelId) {
        List<String> guildIds = SecurityContextUtil.getGuildIds();
        ProjectResponse project = projectService.getByChannelIdForUser(channelId, guildIds);
        return ResponseEntity.ok(RsData.of("200-1", "프로젝트 조회 성공.", project));
    }
}
