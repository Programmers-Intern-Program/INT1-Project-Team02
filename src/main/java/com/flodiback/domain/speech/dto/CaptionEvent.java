package com.flodiback.domain.speech.dto;

import java.time.Instant;

public record CaptionEvent(
        String type,
        Long meetingId,
        String speakerDiscordId,
        String speakerName,
        String text,
        boolean isFinal,
        long sequence,
        Instant sentAt) {

    public static CaptionEvent partial(
            Long meetingId, String speakerDiscordId, String speakerName, String text, long sequence) {
        return new CaptionEvent(
                "caption.partial", meetingId, speakerDiscordId, speakerName, text, false, sequence, Instant.now());
    }

    public static CaptionEvent finalEvent(Long meetingId, String speakerDiscordId, String speakerName, String text) {
        return new CaptionEvent(
                "caption.final", meetingId, speakerDiscordId, speakerName, text, true, 0L, Instant.now());
    }
}
