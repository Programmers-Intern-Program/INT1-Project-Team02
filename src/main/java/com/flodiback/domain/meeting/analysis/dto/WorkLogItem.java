package com.flodiback.domain.meeting.analysis.dto;

/**
 * LLM이 추출한 워크로그 항목입니다.
 *
 * @param assigneeName 담당자 이름
 * @param task         작업 내용
 * @param dueDate      마감일 (YYYY-MM-DD 형식, 없으면 null)
 */
public record WorkLogItem(String assigneeName, String task, String dueDate) {}
