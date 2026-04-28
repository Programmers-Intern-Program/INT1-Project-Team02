package com.flodiback.domain.meeting.meetinglog.dto;

import java.time.LocalDateTime;

import com.flodiback.domain.decision.decision.entity.Decision;

public record DecisionSummary(String content, LocalDateTime decidedAt) {

    public static DecisionSummary from(Decision decision) {
        return new DecisionSummary(decision.getContent(), decision.getDecidedAt());
    }
}
