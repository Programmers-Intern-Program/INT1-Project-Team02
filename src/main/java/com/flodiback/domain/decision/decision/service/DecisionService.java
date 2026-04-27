package com.flodiback.domain.decision.decision.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.decision.decision.dto.DecisionRequest;
import com.flodiback.domain.decision.decision.dto.DecisionResponse;
import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.domain.project.project.entity.Project;
import com.flodiback.domain.project.project.repository.ProjectRepository;
import com.flodiback.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DecisionService {

    private final DecisionRepository decisionRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<DecisionResponse> getDecisions(Long projectId) {
        // 존재하는 프로젝트의 결정사항만 조회하도록 먼저 프로젝트를 확인합니다.
        findProject(projectId);

        return decisionRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(DecisionResponse::from)
                .toList();
    }

    @Transactional
    public DecisionResponse createDecision(Long projectId, DecisionRequest request) {
        // 수동으로 추가하는 결정사항은 회의와 연결하지 않고 프로젝트 기억으로 저장합니다.
        Project project = findProject(projectId);
        Decision decision = Decision.builder()
                .project(project)
                .content(request.content())
                .embedding(null)
                .build();

        Decision savedDecision = decisionRepository.save(decision);

        return DecisionResponse.from(savedDecision);
    }

    @Transactional
    public DecisionResponse updateDecision(Long projectId, Long decisionId, DecisionRequest request) {
        // 요청한 프로젝트에 속한 결정사항만 수정할 수 있도록 소유 프로젝트를 검증합니다.
        findProject(projectId);
        Decision decision = findDecisionInProject(projectId, decisionId);
        decision.updateContent(request.content());

        return DecisionResponse.from(decision);
    }

    @Transactional
    public void deleteDecision(Long projectId, Long decisionId) {
        // 요청한 프로젝트에 속한 결정사항만 삭제할 수 있도록 소유 프로젝트를 검증합니다.
        findProject(projectId);
        Decision decision = findDecisionInProject(projectId, decisionId);
        decisionRepository.delete(decision);
    }

    private Project findProject(Long projectId) {
        return projectRepository
                .findById(projectId)
                .orElseThrow(() -> new ServiceException("404-1", "프로젝트를 찾을 수 없습니다."));
    }

    private Decision findDecisionInProject(Long projectId, Long decisionId) {
        return decisionRepository
                .findByIdAndProjectId(decisionId, projectId)
                .orElseThrow(() -> new ServiceException("404-1", "결정사항을 찾을 수 없습니다."));
    }
}
