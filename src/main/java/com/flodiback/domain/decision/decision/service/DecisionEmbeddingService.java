package com.flodiback.domain.decision.decision.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.decision.decision.repository.DecisionRepository;
import com.flodiback.global.embedding.OpenAiEmbeddingClient;
import com.pgvector.PGvector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionEmbeddingService {

    private final OpenAiEmbeddingClient embeddingClient;
    private final DecisionRepository decisionRepository;

    @Transactional
    public void processEmbedding(Decision decision) {
        if (decision == null) return;

        float[] raw;
        try {
            raw = embeddingClient.embed(decision.getContent());
        } catch (Exception e) {
            log.warn("Embedding API call failed - decisionId={}: {}", decision.getId(), e.getMessage());
            return;
        }

        if (raw == null || raw.length == 0) return;

        String embeddingStr = new PGvector(raw).getValue();
        decisionRepository.updateEmbedding(decision.getId(), embeddingStr);
    }
}
