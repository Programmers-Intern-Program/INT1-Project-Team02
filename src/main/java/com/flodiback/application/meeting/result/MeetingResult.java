package com.flodiback.application.meeting.result;

import java.time.LocalDateTime;

import com.flodiback.domain.meeting.type.MeetingStatus;

public record MeetingResult(
        Long id, Long projectId, String title, LocalDateTime startedAt, LocalDateTime endedAt, MeetingStatus status) {}
