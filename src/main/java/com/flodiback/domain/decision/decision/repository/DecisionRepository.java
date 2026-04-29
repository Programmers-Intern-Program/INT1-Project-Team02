package com.flodiback.domain.decision.decision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flodiback.domain.decision.decision.entity.Decision;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    List<Decision> findByProjectId(Long projectId);

    List<Decision> findByProjectIdOrderByIdAsc(Long projectId);

    Optional<Decision> findByIdAndProjectId(Long id, Long projectId);

    @Modifying
    @Query(value = "UPDATE decisions SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    @Query(value = """
                    WITH semantic AS (
                        SELECT id,
                               1 - (embedding <=> CAST(:embedding AS vector)) AS semantic_score
                        FROM decisions
                        WHERE project_id = :projectId
                          AND embedding IS NOT NULL
                    ),
                    combined AS (
                        SELECT s.id,
                               s.semantic_score * :semanticWeight
                               + COALESCE(ts_rank(d.content_tsv,
                                   plainto_tsquery('simple', :queryText)), 0) * :keywordWeight
                               AS total_score
                        FROM semantic s
                        JOIN decisions d ON s.id = d.id
                    )
                    SELECT d.*
                    FROM decisions d
                    JOIN combined c ON d.id = c.id
                    ORDER BY c.total_score DESC
                    LIMIT :topK
                    """, nativeQuery = true)
    List<Decision> hybridSearch(
            @Param("projectId") Long projectId,
            @Param("embedding") String embedding,
            @Param("queryText") String queryText,
            @Param("topK") int topK,
            @Param("semanticWeight") double semanticWeight,
            @Param("keywordWeight") double keywordWeight);
}
