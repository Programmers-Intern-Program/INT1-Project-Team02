package com.flodiback.api.decision;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.flodiback.domain.decision.decision.dto.DecisionRequest;
import com.flodiback.domain.decision.decision.dto.DecisionResponse;
import com.flodiback.domain.decision.decision.service.DecisionService;
import com.flodiback.global.rsData.RsData;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/decisions")
public class DecisionController {

    private final DecisionService decisionService;

    @GetMapping
    public ResponseEntity<RsData<List<DecisionResponse>>> getDecisions(@PathVariable Long projectId) {
        // Discord 봇의 결정사항 조회 명령어가 호출하는 Public API입니다.
        List<DecisionResponse> responses = decisionService.getDecisions(projectId);

        return ResponseEntity.ok(RsData.of("200-1", "결정사항 목록 조회 성공.", responses));
    }

    @PostMapping
    public ResponseEntity<RsData<DecisionResponse>> createDecision(
            @PathVariable Long projectId, @Valid @RequestBody DecisionRequest request) {
        // 회의와 무관하게 사람이 직접 프로젝트 결정사항을 추가할 때 사용합니다.
        DecisionResponse response = decisionService.createDecision(projectId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(RsData.of("201-1", "결정사항이 추가되었습니다.", response));
    }

    @PutMapping("/{decisionId}")
    public ResponseEntity<RsData<DecisionResponse>> updateDecision(
            @PathVariable Long projectId, @PathVariable Long decisionId, @Valid @RequestBody DecisionRequest request) {
        // 잘못 저장된 결정사항을 Discord 명령어로 수정할 때 사용합니다.
        DecisionResponse response = decisionService.updateDecision(projectId, decisionId, request);

        return ResponseEntity.ok(RsData.of("200-1", "결정사항이 수정되었습니다.", response));
    }

    @DeleteMapping("/{decisionId}")
    public ResponseEntity<RsData<Void>> deleteDecision(@PathVariable Long projectId, @PathVariable Long decisionId) {
        // 더 이상 유효하지 않은 결정사항을 프로젝트 컨텍스트에서 제거합니다.
        decisionService.deleteDecision(projectId, decisionId);

        return ResponseEntity.ok(RsData.of("200-1", "결정사항이 삭제되었습니다."));
    }
}
