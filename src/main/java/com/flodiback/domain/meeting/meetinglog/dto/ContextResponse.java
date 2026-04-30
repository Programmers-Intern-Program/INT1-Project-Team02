package com.flodiback.domain.meeting.meetinglog.dto;

import java.util.Collections;
import java.util.List;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;
import com.flodiback.domain.project.project.entity.Project;

public record ContextResponse(ShortTermContext shortTerm, LongTermContext longTerm) {

    public static ContextResponse of(
            Project project,
            List<Utterance> recentUtterances,
            List<Decision> decisions,
            List<MeetingSummary> pastSummaries) {

        ShortTermContext shortTerm = new ShortTermContext(
                recentUtterances.stream().map(UtteranceSummary::from).toList());

        LongTermContext longTerm = new LongTermContext(
                project.getName(),
                project.getTechStack(),
                project.getMetadata(),
                decisions.stream().map(DecisionSummary::from).toList(),
                pastSummaries.stream().map(PastSummary::from).toList());

        return new ContextResponse(shortTerm, longTerm);
    }

    public static ContextResponse noProject(List<Utterance> recentUtterances) {
        ShortTermContext shortTerm = new ShortTermContext(
                recentUtterances.stream().map(UtteranceSummary::from).toList());

        LongTermContext longTerm =
                new LongTermContext(null, null, null, Collections.emptyList(), Collections.emptyList());

        return new ContextResponse(shortTerm, longTerm);
    }
}
