package com.flodiback.domain.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.dto.InternalSpeechResponse;
import com.flodiback.domain.speech.dto.SavedSpeechResult;

@ExtendWith(MockitoExtension.class)
class InternalSpeechServiceTest {

    @Mock
    private SpeechPersistenceService speechPersistenceService;

    @Mock
    private SpeechAiAnswerService speechAiAnswerService;

    @Mock
    private SpeechAiAnswerAsyncService speechAiAnswerAsyncService;

    @Mock
    private AiAnswerWebSocketPublisher aiAnswerWebSocketPublisher;

    @InjectMocks
    private InternalSpeechService internalSpeechService;

    @Test
    void saveSpeech_triggersAsyncAnswerAfterSpeechIsPersisted_whenWakeWordHasQuestion() {
        InternalSpeechRequest request = request("AI야 인증 방식 뭐로 정했어?");
        given(speechPersistenceService.saveUtterance(request)).willReturn(new SavedSpeechResult(100L, 1L));
        given(speechAiAnswerService.extractQuestion(request.text())).willReturn("인증 방식 뭐로 정했어?");

        InternalSpeechResponse response = internalSpeechService.saveSpeech(request);

        assertThat(response.utteranceId()).isEqualTo(100L);
        assertThat(response.meetingId()).isEqualTo(1L);
        assertThat(response.aiAnswer()).isNull();

        InOrder inOrder = inOrder(speechPersistenceService, speechAiAnswerAsyncService);
        inOrder.verify(speechPersistenceService).saveUtterance(request);
        inOrder.verify(speechAiAnswerAsyncService).generateAndPublish(1L, 100L, "discord-1", "인증 방식 뭐로 정했어?");
    }

    @Test
    void saveSpeech_doesNotTriggerAsyncAnswer_whenQuestionIsBlank() {
        InternalSpeechRequest request = request("이번 회의 목표를 정해봅시다.");
        given(speechPersistenceService.saveUtterance(request)).willReturn(new SavedSpeechResult(101L, 1L));
        given(speechAiAnswerService.extractQuestion(request.text())).willReturn(null);

        InternalSpeechResponse response = internalSpeechService.saveSpeech(request);

        assertThat(response.utteranceId()).isEqualTo(101L);
        assertThat(response.meetingId()).isEqualTo(1L);
        assertThat(response.aiAnswer()).isNull();
        verify(speechAiAnswerAsyncService, never())
                .generateAndPublish(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void saveSpeech_publishesFallback_whenAsyncTaskIsRejected() {
        InternalSpeechRequest request = request("플로디야 토큰 만료 시간 정했어?");
        given(speechPersistenceService.saveUtterance(request)).willReturn(new SavedSpeechResult(102L, 1L));
        given(speechAiAnswerService.extractQuestion(request.text())).willReturn("토큰 만료 시간 정했어?");
        org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException("full"))
                .when(speechAiAnswerAsyncService)
                .generateAndPublish(1L, 102L, "discord-1", "토큰 만료 시간 정했어?");

        InternalSpeechResponse response = internalSpeechService.saveSpeech(request);

        assertThat(response.aiAnswer()).isNull();
        verify(aiAnswerWebSocketPublisher).publishFallback(1L, 102L, "discord-1", "토큰 만료 시간 정했어?", 0L);
    }

    @Test
    void saveSpeech_doesNotOwnTransactionBoundary() throws NoSuchMethodException {
        Method saveSpeech = InternalSpeechService.class.getMethod("saveSpeech", InternalSpeechRequest.class);
        Method saveUtterance = SpeechPersistenceService.class.getMethod("saveUtterance", InternalSpeechRequest.class);

        assertThat(AnnotatedElementUtils.hasAnnotation(InternalSpeechService.class, Transactional.class))
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(saveSpeech, Transactional.class))
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(saveUtterance, Transactional.class))
                .isTrue();
    }

    private InternalSpeechRequest request(String text) {
        return new InternalSpeechRequest(
                1L,
                "discord-1",
                "김철수",
                text,
                LocalDateTime.of(2026, 5, 3, 10, 0),
                LocalDateTime.of(2026, 5, 3, 10, 0, 5));
    }
}
