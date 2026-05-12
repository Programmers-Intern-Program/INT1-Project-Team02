package com.flodiback.domain.project.worklog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.project.worklog.entity.WorkLog;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    List<WorkLog> findTop5ByProjectIdAndStatusOrderByIdDesc(Long projectId, String status);
}
