package com.flodiback.api.project;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@RequestMapping("/api/v1/projects")
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
    public RsData<ProjectResponse> getById(@PathVariable Long id) {
        return RsData.of("200-1", "프로젝트 조회 성공.", projectService.getById(id));
    }

    @GetMapping("/channel/{channelId}")
    public RsData<ProjectResponse> getByChannelId(@PathVariable String channelId) {
        ProjectResponse response = projectService.getByChannelId(channelId);
        if (response == null) {
            return RsData.of("404-1", "채널에 연결된 프로젝트가 없습니다.", null);
        }
        return RsData.of("200-1", "프로젝트 조회 성공.", response);
    }

    @PutMapping("/{id}")
    public RsData<ProjectResponse> update(@PathVariable Long id, @RequestBody UpdateProjectRequest req) {
        return RsData.of("200-1", "프로젝트가 수정되었습니다.", projectService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public RsData<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return RsData.of("200-1", "프로젝트가 삭제되었습니다.");
    }
}
