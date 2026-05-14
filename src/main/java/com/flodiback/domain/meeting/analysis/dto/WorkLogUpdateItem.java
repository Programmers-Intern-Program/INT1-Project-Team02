package com.flodiback.domain.meeting.analysis.dto;

/** AI가 반환하는 기존 작업 로그 상태 변경 항목. */
public record WorkLogUpdateItem(Long id, String status) {}
