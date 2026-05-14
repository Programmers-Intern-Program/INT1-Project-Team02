package com.flodiback.api.worklog;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.meeting.meetinglog.dto.WorkLogSummary;
import com.flodiback.domain.project.worklog.service.WorkLogService;
import com.flodiback.global.rsData.RsData;
import com.flodiback.global.util.SecurityContextUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WebWorkLogController {

    private final WorkLogService workLogService;

    @GetMapping("/api/v1/projects/{id}/work-logs")
    public ResponseEntity<RsData<List<WorkLogSummary>>> getWorkLogs(
            @PathVariable Long id, @RequestParam(required = false) String status) {
        List<String> guildIds = SecurityContextUtil.getGuildIds();
        List<WorkLogSummary> workLogs = workLogService.getByProjectId(id, guildIds, status);
        return ResponseEntity.ok(RsData.of("200-1", "작업 로그 조회 성공.", workLogs));
    }
}
