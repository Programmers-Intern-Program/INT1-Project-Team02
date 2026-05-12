package com.flodiback.domain.speech.dto;

import java.time.LocalDateTime;

public record AiAnswerEvent(
        Long meetingId,
        Long utteranceId,
        String speakerDiscordId,
        String question,
        String answer,
        AiAnswerStatus status,
        long elapsedMs,
        LocalDateTime createdAt) {

    public static AiAnswerEvent of(
            Long meetingId,
            Long utteranceId,
            String speakerDiscordId,
            String question,
            String answer,
            AiAnswerStatus status,
            long elapsedMs) {
        return new AiAnswerEvent(
                meetingId, utteranceId, speakerDiscordId, question, answer, status, elapsedMs, LocalDateTime.now());
    }
}
