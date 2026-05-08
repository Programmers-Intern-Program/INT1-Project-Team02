package com.flodiback.domain.meeting.meetinglog.dto;

import java.util.Collections;
import java.util.List;

public record QuestionContext(List<DecisionSummary> decisions, List<PastSummary> pastSummaries) {

    public static QuestionContext empty() {
        return new QuestionContext(Collections.emptyList(), Collections.emptyList());
    }
}
