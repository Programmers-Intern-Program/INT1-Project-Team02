package com.flodiback.domain.speech.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.flodiback.domain.speech.dto.AiAnswerEvent;
import com.flodiback.domain.speech.dto.AiAnswerStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AiAnswerWebSocketPublisher {

    public static final String PENDING_ANSWER = "답변을 생성 중이에요. 완료되면 여기에 표시됩니다.";
    public static final String FALLBACK_ANSWER = "지금은 답변 생성이 지연되고 있어요. 회의 내용은 저장해두었고, 잠시 후 다시 질문해 주세요.";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishPending(
            Long meetingId, Long utteranceId, String speakerDiscordId, String question, long elapsedMs) {
        publish(meetingId, utteranceId, speakerDiscordId, question, PENDING_ANSWER, AiAnswerStatus.PENDING, elapsedMs);
    }

    public void publishCompleted(
            Long meetingId, Long utteranceId, String speakerDiscordId, String question, String answer, long elapsedMs) {
        publish(meetingId, utteranceId, speakerDiscordId, question, answer, AiAnswerStatus.COMPLETED, elapsedMs);
    }

    public void publishFallback(
            Long meetingId, Long utteranceId, String speakerDiscordId, String question, long elapsedMs) {
        publish(
                meetingId,
                utteranceId,
                speakerDiscordId,
                question,
                FALLBACK_ANSWER,
                AiAnswerStatus.FALLBACK,
                elapsedMs);
    }

    private void publish(
            Long meetingId,
            Long utteranceId,
            String speakerDiscordId,
            String question,
            String answer,
            AiAnswerStatus status,
            long elapsedMs) {
        messagingTemplate.convertAndSend(
                "/topic/meetings/" + meetingId + "/ai-answer",
                AiAnswerEvent.of(meetingId, utteranceId, speakerDiscordId, question, answer, status, elapsedMs));
    }
}
