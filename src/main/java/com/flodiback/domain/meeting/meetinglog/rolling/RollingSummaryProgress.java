package com.flodiback.domain.meeting.meetinglog.rolling;

public record RollingSummaryProgress(
        Long meetingId,
        long uncompressedTokens,
        int thresholdTokens,
        long remainingTokens,
        int progressPercent,
        int remainingPercent,
        boolean compressionTriggered) {}
