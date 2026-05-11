package com.flodiback.domain.meeting.meetinglog.rolling;

public record RollingSummaryUpdatedEvent(Long meetingId, String summary, Integer version) {}
