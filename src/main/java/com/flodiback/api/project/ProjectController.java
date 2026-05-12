package com.flodiback.api.project;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.project.project.dto.CreateProjectRequest;
import com.flodiback.domain.project.project.dto.ProjectResponse;
import com.flodiback.domain.project.project.dto.UpdateProjectRequest;
import com.flodiback.domain.project.project.service.ProjectService;
import com.flodiback.global.rsData.RsData;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public RsData<ProjectResponse> create(@RequestBody @Valid CreateProjectRequest req) {
        return RsData.of("201-1", "프로젝트가 생성되었습니다.", projectService.create(req));
    }

    @GetMapping
    public RsData<List<ProjectResponse>> getAll() {
        return RsData.of("200-1", "프로젝트 목록 조회 성공.", projectService.getAll());
    }

    @GetMapping("/{id}")
    public RsData<ProjectResponse> getById(
            @PathVariable Long id, @RequestHeader(name = "X-Guild-Id", required = false) String guildId) {
        return RsData.of("200-1", "프로젝트 조회 성공.", projectService.getById(id, guildId));
    }

    @GetMapping("/channel/{channelId}")
    public RsData<ProjectResponse> getByChannelId(
            @PathVariable String channelId, @RequestHeader(name = "X-Guild-Id", required = false) String guildId) {
        return RsData.of("200-1", "프로젝트 조회 성공.", projectService.getByChannelId(channelId, guildId));
    }

    @PutMapping("/{id}")
    public RsData<ProjectResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateProjectRequest req,
            @RequestHeader(name = "X-Requester-Id", required = false) String requesterId) {
        return RsData.of("200-1", "프로젝트가 수정되었습니다.", projectService.update(id, req, requesterId));
    }

    @PutMapping("/{id}/channel/{channelId}")
    public RsData<Void> connectChannel(
            @PathVariable Long id,
            @PathVariable String channelId,
            @RequestHeader(name = "X-Requester-Id", required = false) String requesterId) {
        projectService.connectChannel(id, channelId, requesterId);
        return RsData.of("200-1", "채널이 연결되었습니다.");
    }

    @DeleteMapping("/{id}/channel")
    public RsData<Void> disconnectChannel(
            @PathVariable Long id, @RequestHeader(name = "X-Requester-Id", required = false) String requesterId) {
        projectService.disconnectChannel(id, requesterId);
        return RsData.of("200-1", "채널 연결이 해제되었습니다.");
    }

    @DeleteMapping("/{id}")
    public RsData<Void> delete(
            @PathVariable Long id, @RequestHeader(name = "X-Requester-Id", required = false) String requesterId) {
        projectService.delete(id, requesterId);
        return RsData.of("200-1", "프로젝트가 삭제되었습니다.");
    }
}
