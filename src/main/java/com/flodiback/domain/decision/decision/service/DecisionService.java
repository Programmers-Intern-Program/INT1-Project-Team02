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
    private final DecisionEmbeddingService decisionEmbeddingService;

    @Transactional(readOnly = true)
    public List<DecisionResponse> getDecisions(Long projectId) {
        findProject(projectId);
        return decisionRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(DecisionResponse::from)
                .toList();
    }

    @Transactional
    public DecisionResponse createDecision(Long projectId, DecisionRequest request) {
        Project project = findProject(projectId);
        Decision saved = decisionRepository.save(
                Decision.builder().project(project).content(request.content()).build());
        decisionEmbeddingService.processEmbedding(saved);
        return DecisionResponse.from(saved);
    }

    @Transactional
    public DecisionResponse updateDecision(Long projectId, Long decisionId, DecisionRequest request) {
        findProject(projectId);
        Decision decision = findDecisionInProject(projectId, decisionId);
        decision.updateContent(request.content());
        Decision saved = decisionRepository.save(decision);
        decisionEmbeddingService.processEmbedding(saved);
        return DecisionResponse.from(saved);
    }

    @Transactional
    public void deleteDecision(Long projectId, Long decisionId) {
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
