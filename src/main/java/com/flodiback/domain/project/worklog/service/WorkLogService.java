package com.flodiback.domain.project.worklog.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.meeting.meetinglog.dto.WorkLogSummary;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.domain.project.worklog.repository.WorkLogRepository;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkLogService {

    private final WorkLogRepository workLogRepository;
    private final ProjectRepository projectRepository;

    public List<WorkLogSummary> getByProjectId(Long projectId, List<String> guildIds, String status) {
        Project project =
                projectRepository.findById(projectId).orElseThrow(() -> new NoSuchElementException("존재하지 않는 프로젝트입니다."));

        if (project.getServer() != null
                && !guildIds.contains(project.getServer().getGuildId())) {
            throw new ServiceException("403-1", "권한이 없습니다.");
        }

        if (status != null && !status.isBlank()) {
            return workLogRepository
                    .findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status.toUpperCase())
                    .stream()
                    .map(WorkLogSummary::from)
                    .toList();
        }

        return workLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(WorkLogSummary::from)
                .toList();
    }
}
