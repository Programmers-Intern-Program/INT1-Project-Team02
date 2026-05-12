package com.flodiback.domain.project.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flodiback.domain.project.project.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByChannelId(String channelId);

    List<Project> findByServerGuildIdIn(List<String> guildIds);
}
