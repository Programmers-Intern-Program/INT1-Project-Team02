package com.flodiback.domain.meeting.meetinglog.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateContextRequest(
        @NotNull Long meetingId,
        @NotBlank String summary,
        String unresolvedItems,
        List<String> decisions,
        @Valid List<ActionItemRequest> actionItems) {}
