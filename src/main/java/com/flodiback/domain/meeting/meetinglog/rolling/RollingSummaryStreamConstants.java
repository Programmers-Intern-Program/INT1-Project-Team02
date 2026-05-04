package com.flodiback.domain.meeting.meetinglog.rolling;

public final class RollingSummaryStreamConstants {

    public static final String STREAM_KEY = "rolling-summary-utterances";
    public static final String GROUP_NAME = "rolling-summary-group";
    public static final String CONSUMER_NAME = "rolling-summary-consumer-1";
    public static final int TOKEN_THRESHOLD = 3000;
    public static final int KEEP_TURNS = 20;
    public static final int GRACE_SECONDS = 10;

    private RollingSummaryStreamConstants() {}
}
