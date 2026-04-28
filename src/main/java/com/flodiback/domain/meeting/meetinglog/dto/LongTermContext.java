package com.flodiback.domain.meeting.meetinglog.dto;

import java.util.List;

public record LongTermContext(
        String projectName,
        String techStack,
        String metadata,
        List<DecisionSummary> decisions,
        List<PastSummary> pastSummaries) {}
