package com.flodiback.domain.meeting.meetinglog.dto;

import java.util.List;

public record ShortTermContext(List<UtteranceSummary> recentUtterances) {}
