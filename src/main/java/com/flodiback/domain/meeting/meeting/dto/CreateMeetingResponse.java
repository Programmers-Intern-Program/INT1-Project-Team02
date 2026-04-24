package com.flodiback.domain.meeting.meeting.dto;

import java.time.LocalDateTime;

import com.flodiback.global.enums.MeetingStatus;

public record CreateMeetingResponse(
        Long id, Long projectId, String title, LocalDateTime startedAt, MeetingStatus status) {}
