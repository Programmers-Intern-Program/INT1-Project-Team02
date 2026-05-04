package com.flodiback.domain.meeting.meeting.context;

import java.util.Collections;
import java.util.List;

import com.flodiback.domain.meeting.meetinglog.dto.DecisionSummary;
import com.flodiback.domain.meeting.meetinglog.dto.PastSummary;

public record MeetingStartContext(
        String projectName,
        String techStack,
        String metadata,
        List<DecisionSummary> recentDecisions,
        List<PastSummary> recentSummaries) {

    public static MeetingStartContext noProject() {
        return new MeetingStartContext(null, null, null, Collections.emptyList(), Collections.emptyList());
    }
}
