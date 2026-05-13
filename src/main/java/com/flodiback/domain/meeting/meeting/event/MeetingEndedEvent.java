package com.flodiback.domain.meeting.meeting.event;

public record MeetingEndedEvent(Long meetingId, Long projectId, String channelId) {}
