package com.flodiback.domain.meeting.analysis.dto;

import java.util.List;

/**
 * LLM 응답을 파싱한 회의 분석 결과입니다.
 *
 * @param summary          전체 회의 요약
 * @param unresolvedItems  미결 사항 (없으면 null)
 * @param worklogs         추출된 워크로그 목록
 * @param decisions        추출된 결정사항 목록
 */
public record AnalysisResult(
        String summary, String unresolvedItems, List<WorkLogItem> worklogs, List<DecisionItem> decisions) {}
