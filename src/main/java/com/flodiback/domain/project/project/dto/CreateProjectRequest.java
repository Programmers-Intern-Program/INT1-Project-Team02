package com.flodiback.domain.project.project.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(@NotBlank String name, String description, String techStack, Long serverId) {}
