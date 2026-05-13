package com.flodiback.domain.meeting.meeting.event;

public record MeetingStartedEvent(Long meetingId, Long projectId, String channelId) {}
