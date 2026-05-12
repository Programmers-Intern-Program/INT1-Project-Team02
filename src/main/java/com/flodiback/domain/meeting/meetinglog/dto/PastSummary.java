package com.flodiback.domain.meeting.meetinglog.dto;

import java.time.LocalDateTime;

import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;

public record PastSummary(Long id, String summary, LocalDateTime createdAt) {

    public static PastSummary from(MeetingSummary meetingSummary) {
        return new PastSummary(meetingSummary.getId(), meetingSummary.getSummary(), meetingSummary.getCreatedAt());
    }
}
