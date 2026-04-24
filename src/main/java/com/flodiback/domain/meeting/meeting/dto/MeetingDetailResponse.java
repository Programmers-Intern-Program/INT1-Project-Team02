package com.flodiback.domain.meeting.meeting.dto;

import java.time.LocalDateTime;

import com.flodiback.global.enums.MeetingStatus;

public record MeetingDetailResponse(
        Long id, Long projectId, String title, LocalDateTime startedAt, LocalDateTime endedAt, MeetingStatus status) {}
