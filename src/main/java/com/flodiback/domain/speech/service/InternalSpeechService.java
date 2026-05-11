package com.flodiback.domain.speech.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.dto.InternalSpeechResponse;
import com.flodiback.domain.speech.dto.SavedSpeechResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalSpeechService {

    private final SpeechPersistenceService speechPersistenceService;
    private final SpeechAiAnswerService speechAiAnswerService;
    private final SpeechAiAnswerAsyncService speechAiAnswerAsyncService;
    private final AiAnswerWebSocketPublisher aiAnswerWebSocketPublisher;

    public InternalSpeechResponse saveSpeech(InternalSpeechRequest request) {
        SavedSpeechResult savedSpeech = speechPersistenceService.saveUtterance(request);

        String question = speechAiAnswerService.extractQuestion(request.text());
        if (StringUtils.hasText(question)) {
            try {
                speechAiAnswerAsyncService.generateAndPublish(
                        savedSpeech.meetingId(), savedSpeech.utteranceId(), request.speakerDiscordId(), question);
            } catch (RuntimeException e) {
                log.warn(
                        "AI answer task rejected, fallback published. meetingId={}, utteranceId={}, reason={}",
                        savedSpeech.meetingId(),
                        savedSpeech.utteranceId(),
                        e.getMessage());
                aiAnswerWebSocketPublisher.publishFallback(
                        savedSpeech.meetingId(), savedSpeech.utteranceId(), request.speakerDiscordId(), question, 0L);
            }
        }

        return new InternalSpeechResponse(savedSpeech.utteranceId(), savedSpeech.meetingId(), null);
    }
}
