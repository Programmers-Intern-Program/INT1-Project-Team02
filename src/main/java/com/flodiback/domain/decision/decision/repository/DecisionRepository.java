package com.flodiback.domain.decision.decision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.decision.decision.entity.Decision;

public interface DecisionRepository extends JpaRepository<Decision, Long> {

    List<Decision> findByProjectId(Long projectId);
    List<Decision> findByProjectIdOrderByIdAsc(Long projectId);

    Optional<Decision> findByIdAndProjectId(Long id, Long projectId);
}
