package com.flodiback.domain.project.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.project.project.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {}
