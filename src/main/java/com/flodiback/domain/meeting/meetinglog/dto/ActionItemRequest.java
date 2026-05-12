package com.flodiback.domain.meeting.meetinglog.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record ActionItemRequest(
        String assigneeDiscordId,
        @NotBlank String assigneeName,
        @NotBlank String task,
        LocalDate dueDate) {

    public ActionItemRequest(String assigneeName, String task, LocalDate dueDate) {
        this(null, assigneeName, task, dueDate);
    }
}
