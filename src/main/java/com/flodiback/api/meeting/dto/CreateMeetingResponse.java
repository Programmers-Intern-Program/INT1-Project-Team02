package com.flodiback.api.meeting.dto;

import java.time.LocalDateTime;

import com.flodiback.application.meeting.result.MeetingResult;
import com.flodiback.domain.meeting.type.MeetingStatus;

public record CreateMeetingResponse(
        Long id, Long projectId, String title, LocalDateTime startedAt, MeetingStatus status) {

    public static CreateMeetingResponse from(MeetingResult result) {
        return new CreateMeetingResponse(
                result.id(), result.projectId(), result.title(), result.startedAt(), result.status());
    }
}
