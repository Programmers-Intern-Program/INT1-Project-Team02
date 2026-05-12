package com.flodiback.domain.meeting.analysis.dto;

import java.time.LocalDate;

public record WorkLogItem(String assigneeSpeakerKey, String task, LocalDate dueDate) {}
