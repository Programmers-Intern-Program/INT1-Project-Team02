package com.flodiback.domain.meeting.analysis.dto;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record AnalysisResult(
        String summary,

        @JsonDeserialize(using = UnresolvedItemsDeserializer.class)
        String unresolvedItems,

        List<WorkLogItem> worklogs,
        List<WorkLogUpdateItem> worklogUpdates,
        List<DecisionItem> decisions) {}
