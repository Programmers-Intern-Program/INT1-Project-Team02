package com.flodiback.domain.speech.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InternalSpeechResponse(
        // 저장된 발화 ID입니다.
        @JsonProperty("utterance_id") Long utteranceId,

        // 발화가 저장된 회의 ID입니다.
        @JsonProperty("meeting_id") Long meetingId) {}
