package com.flodiback.domain.meeting.meetinglog.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record ActionItemRequest(
        @NotBlank String assigneeName, @NotBlank String task, LocalDate dueDate) {}
