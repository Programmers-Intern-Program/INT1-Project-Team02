package com.flodiback.domain.meeting.meeting.context;

import java.util.Collections;
import java.util.List;

import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;
import com.flodiback.domain.meeting.meetinglog.dto.WorkLogSummary;

public record MeetingStartContext(
        Long meetingId,
        Long projectId,
        String projectName,
        String techStack,
        String metadata,
        List<DecisionSummary> recentDecisions,
        List<PastSummary> recentSummaries,
        String unresolvedItems,
        List<WorkLogSummary> activeWorkLogs) {

    public static MeetingStartContext noProject(Long meetingId) {
        return new MeetingStartContext(
                meetingId,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyList());
    }
}
