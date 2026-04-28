package com.flodiback.domain.decision.decision.dto;

import java.time.LocalDateTime;

import com.flodiback.domain.decision.decision.entity.Decision;

public record DecisionResponse(
        // 저장된 결정사항 ID입니다.
        Long id,

        // 결정사항이 속한 프로젝트 ID입니다.
        Long projectId,

        // 회의 종료 분석으로 생성된 경우 연결되는 회의 ID입니다.
        Long meetingId,

        // 결정사항 본문입니다.
        String content,

        // 결정사항이 저장된 시각입니다.
        LocalDateTime decidedAt) {

    public static DecisionResponse from(Decision decision) {
        return new DecisionResponse(
                decision.getId(),
                decision.getProject().getId(),
                decision.getMeeting() != null ? decision.getMeeting().getId() : null,
                decision.getContent(),
                decision.getDecidedAt());
    }
}
