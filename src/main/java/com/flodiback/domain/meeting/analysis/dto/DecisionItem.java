package com.flodiback.domain.meeting.analysis.dto;

/**
 * LLM이 추출한 결정사항 항목입니다.
 *
 * @param content 결정 내용
 */
public record DecisionItem(String content) {}
