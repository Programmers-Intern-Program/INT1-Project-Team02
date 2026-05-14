package com.flodiback.domain.project.project.dto;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        Long serverId,
        String serverName,
        String name,
        String description,
        String techStack,
        LocalDateTime createdAt,
        String channelId,
        String channelName,
        Long activeMeetingId) {}
