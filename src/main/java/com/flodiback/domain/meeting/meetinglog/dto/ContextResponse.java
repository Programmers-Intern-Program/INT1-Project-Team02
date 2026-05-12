package com.flodiback.domain.meeting.meetinglog.dto;

import java.util.List;

import com.flodiback.domain.decision.decision.entity.Decision;
import com.flodiback.domain.meeting.meeting.context.MeetingStartContext;
import com.flodiback.domain.meeting.meetinglog.entity.MeetingSummary;
import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public record ContextResponse(
        MeetingStartContext startContext, ShortTermContext shortTerm, QuestionContext questionContext) {

    public static ContextResponse of(
            MeetingStartContext startContext,
            String rollingSummary,
            List<Utterance> recentUtterances,
            QuestionContext questionContext) {

        ShortTermContext shortTerm = new ShortTermContext(
                rollingSummary,
                recentUtterances.stream().map(UtteranceSummary::from).toList());

        return new ContextResponse(startContext, shortTerm, questionContext);
    }

    public static ContextResponse of(
            MeetingStartContext startContext,
            String rollingSummary,
            List<Utterance> recentUtterances,
            List<Decision> decisions,
            List<MeetingSummary> pastSummaries) {
        return of(
                startContext,
                rollingSummary,
                recentUtterances,
                new QuestionContext(
                        decisions.stream().map(DecisionSummary::from).toList(),
                        pastSummaries.stream().map(PastSummary::from).toList()));
    }

    public static ContextResponse noProject(String rollingSummary, List<Utterance> recentUtterances) {
        return noProject(MeetingStartContext.noProject(null), rollingSummary, recentUtterances);
    }

    public static ContextResponse noProject(
            MeetingStartContext startContext, String rollingSummary, List<Utterance> recentUtterances) {
        ShortTermContext shortTerm = new ShortTermContext(
                rollingSummary,
                recentUtterances.stream().map(UtteranceSummary::from).toList());

        return new ContextResponse(startContext, shortTerm, QuestionContext.empty());
    }
}
