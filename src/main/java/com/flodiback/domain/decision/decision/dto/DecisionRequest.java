package com.flodiback.domain.decision.decision.dto;

import jakarta.validation.constraints.NotBlank;

public record DecisionRequest(
        // 프로젝트에 저장할 결정사항 본문입니다.
        @NotBlank String content) {}
