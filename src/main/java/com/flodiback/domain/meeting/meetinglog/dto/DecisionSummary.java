package com.flodiback.domain.meeting.meetinglog.dto;

import java.time.LocalDateTime;

import com.flodiback.domain.decision.decision.entity.Decision;

public record DecisionSummary(Long id, String content, LocalDateTime decidedAt) {

    public static DecisionSummary from(Decision decision) {
        return new DecisionSummary(decision.getId(), decision.getContent(), decision.getDecidedAt());
    }
}
