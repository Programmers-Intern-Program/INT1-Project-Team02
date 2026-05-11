package com.flodiback.domain.speech.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SpeechAiAnswerAsyncServiceTest {

    private SpeechAiAnswerService speechAiAnswerService;
    private AiAnswerWebSocketPublisher publisher;
    private ScheduledExecutorService pendingScheduler;
    private ScheduledFuture<?> pendingTask;
    private AtomicReference<Runnable> pendingRunnable;
    private SpeechAiAnswerAsyncService service;

    @BeforeEach
    void setUp() {
        speechAiAnswerService = mock(SpeechAiAnswerService.class);
        publisher = mock(AiAnswerWebSocketPublisher.class);
        pendingScheduler = mock(ScheduledExecutorService.class);
        pendingTask = mock(ScheduledFuture.class);
        pendingRunnable = new AtomicReference<>();
        given(pendingScheduler.schedule(org.mockito.ArgumentMatchers.any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS)))
                .willAnswer(invocation -> {
                    pendingRunnable.set(invocation.getArgument(0));
                    return pendingTask;
                });
        service = new SpeechAiAnswerAsyncService(speechAiAnswerService, publisher, pendingScheduler);
    }

    @Test
    void generateAndPublish_publishesCompletedOnly_whenAnswerCompletesBeforePending() {
        given(speechAiAnswerService.generateAnswer(1L, "question")).willReturn("answer");

        service.generateAndPublish(1L, 100L, "discord-1", "question");

        verify(pendingTask).cancel(false);
        verify(publisher).publishCompleted(eq(1L), eq(100L), eq("discord-1"), eq("question"), eq("answer"), anyLong());
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.never())
                .publishPending(
                        anyLong(),
                        anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        anyLong());
    }

    @Test
    void generateAndPublish_publishesPendingThenCompleted_whenPendingTimerRunsFirst() {
        given(speechAiAnswerService.generateAnswer(1L, "question")).willAnswer(invocation -> {
            pendingRunnable.get().run();
            return "answer";
        });

        service.generateAndPublish(1L, 100L, "discord-1", "question");

        InOrder inOrder = inOrder(publisher);
        inOrder.verify(publisher).publishPending(eq(1L), eq(100L), eq("discord-1"), eq("question"), anyLong());
        inOrder.verify(publisher)
                .publishCompleted(eq(1L), eq(100L), eq("discord-1"), eq("question"), eq("answer"), anyLong());
    }

    @Test
    void generateAndPublish_publishesFallback_whenAnswerFails() {
        given(speechAiAnswerService.generateAnswer(1L, "question")).willThrow(new RuntimeException("GLM failed"));

        service.generateAndPublish(1L, 100L, "discord-1", "question");

        verify(pendingTask).cancel(false);
        verify(publisher).publishFallback(eq(1L), eq(100L), eq("discord-1"), eq("question"), anyLong());
    }
}
