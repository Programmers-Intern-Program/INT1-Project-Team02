package com.flodiback.domain.project.worklog.event;

public record WorkLogChangedEvent(Long projectId, Long meetingId) {}
