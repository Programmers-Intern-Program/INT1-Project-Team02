package com.flodiback.domain.meeting.meetinglog.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateContextRequest(
        @NotNull Long meetingId,
        @NotBlank String summary,
        String unresolvedItems,
        List<String> decisions,
        @Valid List<ActionItemRequest> actionItems,
        @Valid List<WorkLogStatusUpdate> worklogUpdates) {

    public record WorkLogStatusUpdate(
            @NotNull Long id,

            @NotBlank @Pattern(regexp = "TODO|IN_PROGRESS|DONE|CANCELLED", flags = Pattern.Flag.CASE_INSENSITIVE) String status) {}
}
