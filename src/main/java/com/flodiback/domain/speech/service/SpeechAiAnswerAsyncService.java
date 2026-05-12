package com.flodiback.domain.speech.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SpeechAiAnswerAsyncService {

    private static final long PENDING_DELAY_SECONDS = 5L;

    private final SpeechAiAnswerService speechAiAnswerService;
    private final AiAnswerWebSocketPublisher publisher;

    @Qualifier("pendingScheduler") private final ScheduledExecutorService pendingScheduler;

    public SpeechAiAnswerAsyncService(
            SpeechAiAnswerService speechAiAnswerService,
            AiAnswerWebSocketPublisher publisher,
            @Qualifier("pendingScheduler") ScheduledExecutorService pendingScheduler) {
        this.speechAiAnswerService = speechAiAnswerService;
        this.publisher = publisher;
        this.pendingScheduler = pendingScheduler;
    }

    @Async("aiAnswerExecutor")
    public void generateAndPublish(Long meetingId, Long utteranceId, String speakerDiscordId, String question) {
        long startedAtNanos = System.nanoTime();
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean pendingPublished = new AtomicBoolean(false);
        Object publishLock = new Object();

        ScheduledFuture<?> pendingTask = pendingScheduler.schedule(
                () -> publishPendingIfStillRunning(
                        meetingId,
                        utteranceId,
                        speakerDiscordId,
                        question,
                        startedAtNanos,
                        completed,
                        pendingPublished,
                        publishLock),
                PENDING_DELAY_SECONDS,
                TimeUnit.SECONDS);

        try {
            String answer = speechAiAnswerService.generateAnswer(meetingId, question);
            pendingTask.cancel(false);
            synchronized (publishLock) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                long elapsedMs = elapsedMillis(startedAtNanos);
                if (StringUtils.hasText(answer)) {
                    publisher.publishCompleted(
                            meetingId, utteranceId, speakerDiscordId, question, answer.strip(), elapsedMs);
                    log.info(
                            "AI answer completed. meetingId={}, utteranceId={}, elapsedMs={}, pendingPublished={}",
                            meetingId,
                            utteranceId,
                            elapsedMs,
                            pendingPublished.get());
                } else {
                    publisher.publishFallback(meetingId, utteranceId, speakerDiscordId, question, elapsedMs);
                    log.warn(
                            "AI answer blank, fallback published. meetingId={}, utteranceId={}, elapsedMs={}, pendingPublished={}",
                            meetingId,
                            utteranceId,
                            elapsedMs,
                            pendingPublished.get());
                }
            }
        } catch (RuntimeException e) {
            pendingTask.cancel(false);
            synchronized (publishLock) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                long elapsedMs = elapsedMillis(startedAtNanos);
                publisher.publishFallback(meetingId, utteranceId, speakerDiscordId, question, elapsedMs);
                log.warn(
                        "AI answer failed, fallback published. meetingId={}, utteranceId={}, elapsedMs={}, pendingPublished={}, reason={}",
                        meetingId,
                        utteranceId,
                        elapsedMs,
                        pendingPublished.get(),
                        e.getMessage());
            }
        }
    }

    private void publishPendingIfStillRunning(
            Long meetingId,
            Long utteranceId,
            String speakerDiscordId,
            String question,
            long startedAtNanos,
            AtomicBoolean completed,
            AtomicBoolean pendingPublished,
            Object publishLock) {
        synchronized (publishLock) {
            if (completed.get()) {
                return;
            }
            long elapsedMs = elapsedMillis(startedAtNanos);
            pendingPublished.set(true);
            publisher.publishPending(meetingId, utteranceId, speakerDiscordId, question, elapsedMs);
            log.info(
                    "AI answer pending published. meetingId={}, utteranceId={}, elapsedMs={}",
                    meetingId,
                    utteranceId,
                    elapsedMs);
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
