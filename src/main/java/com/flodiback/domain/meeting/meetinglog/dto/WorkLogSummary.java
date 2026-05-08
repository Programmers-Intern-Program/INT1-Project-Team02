package com.flodiback.domain.meeting.meetinglog.dto;

import java.time.LocalDate;

import com.flodiback.domain.project.worklog.entity.WorkLog;

public record WorkLogSummary(Long id, String assigneeName, String task, LocalDate dueDate, String status) {

    public static WorkLogSummary from(WorkLog workLog) {
        return new WorkLogSummary(
                workLog.getId(),
                workLog.getAssigneeName(),
                workLog.getTask(),
                workLog.getDueDate(),
                workLog.getStatus());
    }
}
