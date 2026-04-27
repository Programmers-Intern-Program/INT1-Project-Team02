package com.flodiback.api.meeting.dto;

import com.flodiback.application.meeting.command.CreateMeetingCommand;

public record CreateMeetingRequest(Long projectId, String title) {

    public CreateMeetingCommand toCommand() {
        return new CreateMeetingCommand(projectId, title);
    }
}
