package com.flodiback.domain.meeting.meetinglog.dto;

import java.time.LocalDateTime;

import com.flodiback.domain.meeting.meetinglog.entity.Utterance;

public record UtteranceSummary(String speakerName, String content, LocalDateTime spokenAt) {

    public static UtteranceSummary from(Utterance utterance) {
        return new UtteranceSummary(utterance.getSpeakerName(), utterance.getContent(), utterance.getSpokenAt());
    }
}
