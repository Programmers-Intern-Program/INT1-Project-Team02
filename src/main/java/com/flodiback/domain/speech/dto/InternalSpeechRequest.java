package com.flodiback.domain.speech.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalSpeechRequest(
        // STT 결과가 연결될 회의 ID입니다.
        @JsonProperty("meeting_id") @NotNull Long meetingId,

        // Discord에서 화자를 식별하는 사용자 ID입니다.
        @JsonProperty("speaker_discord_id") @NotBlank String speakerDiscordId,

        // 회의록에 표시할 화자 이름입니다.
        @JsonProperty("speaker_name") @NotBlank String speakerName,

        // STT가 변환한 발화 텍스트입니다.
        @NotBlank String text,

        // 발화 시작 시각 (봇 STT startMs, Unix epoch 변환).
        @JsonProperty("speech_started_at") @NotNull LocalDateTime speechStartedAt,

        // 발화 종료 시각 (봇 STT endMs, Unix epoch 변환).
        @JsonProperty("speech_ended_at") LocalDateTime speechEndedAt) {}
