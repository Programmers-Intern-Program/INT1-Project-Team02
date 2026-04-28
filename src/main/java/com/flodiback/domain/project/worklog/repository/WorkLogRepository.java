package com.flodiback.domain.project.worklog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.project.worklog.entity.WorkLog;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {}
