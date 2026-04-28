package com.flodiback.api.project;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;
import com.flodiback.global.rsData.RsData;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/projects")
@RequiredArgsConstructor
public class ProjectContextController {

    private final ContextService contextService;

    @PutMapping("/{projectId}/context")
    public ResponseEntity<RsData<Void>> updateContext(
            @PathVariable Long projectId, @RequestBody @Valid UpdateContextRequest req) {
        contextService.updateContext(projectId, req);
        return ResponseEntity.ok(RsData.of("200-1", "컨텍스트가 업데이트되었습니다."));
    }
}
