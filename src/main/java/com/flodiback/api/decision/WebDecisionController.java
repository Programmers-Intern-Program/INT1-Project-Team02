package com.flodiback.api.decision;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.decision.decision.dto.DecisionResponse;
import com.flodiback.domain.decision.decision.service.DecisionService;
import com.flodiback.domain.project.project.service.ProjectService;
import com.flodiback.global.rsData.RsData;
import com.flodiback.global.util.SecurityContextUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class WebDecisionController {

    private final ProjectService projectService;
    private final DecisionService decisionService;

    @GetMapping("/{projectId}/decisions")
    public ResponseEntity<RsData<List<DecisionResponse>>> getDecisions(@PathVariable Long projectId) {
        List<String> guildIds = SecurityContextUtil.getGuildIds();
        projectService.getByIdForUser(projectId, guildIds);
        List<DecisionResponse> decisions = decisionService.getDecisions(projectId);
        return ResponseEntity.ok(RsData.of("200-1", "결정사항 목록 조회 성공.", decisions));
    }
}
