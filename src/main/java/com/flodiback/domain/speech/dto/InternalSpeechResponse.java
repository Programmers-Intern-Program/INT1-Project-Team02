package com.flodiback.domain.speech.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InternalSpeechResponse(
        // 저장된 발화 ID입니다.
        @JsonProperty("utterance_id") Long utteranceId,

        // 발화가 저장된 회의 ID입니다.
        @JsonProperty("meeting_id") Long meetingId,

        // 호출어가 감지되어 생성된 AI 답변입니다. 답변이 없으면 null입니다.
        @JsonProperty("ai_answer") String aiAnswer) {}
