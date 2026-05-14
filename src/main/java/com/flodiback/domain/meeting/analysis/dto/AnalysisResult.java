package com.flodiback.domain.meeting.analysis.dto;

import java.util.List;

public record AnalysisResult(
        String summary,
        String unresolvedItems,
        List<WorkLogItem> worklogs,
        List<WorkLogUpdateItem> worklogUpdates,
        List<DecisionItem> decisions) {}
