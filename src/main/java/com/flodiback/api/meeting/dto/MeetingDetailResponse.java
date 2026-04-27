package com.flodiback.api.meeting.dto;

import java.time.LocalDateTime;

import com.flodiback.application.meeting.result.MeetingResult;
import com.flodiback.domain.meeting.type.MeetingStatus;

public record MeetingDetailResponse(
        Long id, Long projectId, String title, LocalDateTime startedAt, LocalDateTime endedAt, MeetingStatus status) {

    public static MeetingDetailResponse from(MeetingResult result) {
        return new MeetingDetailResponse(
                result.id(), result.projectId(), result.title(), result.startedAt(), result.endedAt(), result.status());
    }
}
