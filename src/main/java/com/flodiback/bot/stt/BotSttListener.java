package com.flodiback.bot.stt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flodiback.bot.BotEnv;
import com.flodiback.domain.speech.stt.SttListener;
import com.flodiback.domain.speech.stt.SttResult;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * STT 결과 소비자.
 *
 * <p>역할:
 * - STT 결과 중 최종본(isFinal=true)만 선택
 * - 내부 API `/internal/v1/speech`로 전달
 */
public class BotSttListener implements SttListener {
    private static final Logger log = LoggerFactory.getLogger(BotSttListener.class);
    private static final List<String> WAKE_WORDS = List.of("AI야", "ai야", "봇아", "클로드야", "플로디야", "flodiya", "plodiya");
    private static final long CAPTION_DEBOUNCE_MS = 300L;
    private static final int CAPTION_MIN_CHARS = 2;
    private static final ScheduledExecutorService CAPTION_DEBOUNCE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new CaptionDebounceThreadFactory());

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final long meetingId;
    private final String speakerDiscordId;
    private final String speakerName;
    private final String internalBaseUrl;
    private final String internalApiKey;
    private final MessageChannel captionChannel;
    private final AtomicReference<String> liveCaptionMessageId = new AtomicReference<>();
    private final AtomicReference<String> desiredCaptionContent = new AtomicReference<>("");
    private final AtomicReference<String> renderedCaptionContent = new AtomicReference<>("");
    private final AtomicBoolean desiredCaptionFinal = new AtomicBoolean(false);
    private final AtomicBoolean captionMessageCreating = new AtomicBoolean(false);
    private final AtomicBoolean captionEditInFlight = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> pendingPartialTask = new AtomicReference<>();
    private final AtomicReference<String> pendingPartialText = new AtomicReference<>("");
    private final AtomicLong pendingPartialVersion = new AtomicLong();

    public BotSttListener(long meetingId, String speakerDiscordId, String speakerName) {
        this(meetingId, speakerDiscordId, speakerName, null);
    }

    public BotSttListener(long meetingId, String speakerDiscordId, String speakerName, MessageChannel captionChannel) {
        this.meetingId = meetingId;
        this.speakerDiscordId = speakerDiscordId;
        this.speakerName = normalizeSpeakerName(speakerName, speakerDiscordId);
        this.internalBaseUrl = normalizeBaseUrl(BotEnv.getOrDefault("INTERNAL_API_BASE_URL", "http://localhost:8080"));
        this.internalApiKey = BotEnv.get("INTERNAL_API_KEY");
        this.captionChannel = captionChannel;
    }

    @Override
    public void onResult(SttResult result) {
        // 중간 결과(delta)는 저장 API로 보내지 않는다.
        if (!result.isFinal()) {
            String partialText = result.text();
            if (partialText != null && !partialText.isBlank()) {
                int charCount = partialText.codePointCount(0, partialText.length());
                if (charCount >= CAPTION_MIN_CHARS) {
                    schedulePartialCaptionUpdate(partialText);
                }
                log.info(
                        "[STT/중간텍스트] sessionId={}, speakerId={}, meetingId={}, text={}",
                        result.sessionId(),
                        speakerDiscordId,
                        meetingId,
                        partialText);
            }
            return;
        }

        // 최종 텍스트가 비어 있으면 무시한다.
        String text = result.text();
        if (text == null || text.isBlank()) {
            return;
        }

        cancelPendingPartialTask();
        upsertLiveCaption(text, true);

        try {
            // Spring이 관리하는 ObjectMapper가 아니므로 LocalDateTime 직렬화 설정이 없다.
            // 그래서 내부 API 계약에 맞는 JSON을 직접 만들고 시각은 ISO 문자열로 넣는다.
            ObjectNode body = objectMapper.createObjectNode();
            body.put("meeting_id", meetingId);
            body.put("speaker_discord_id", speakerDiscordId);
            body.put("speaker_name", speakerName);
            body.put("text", text);
            body.put(
                    "speech_started_at",
                    epochMsToLocalDateTime(result.startMs()).toString());
            body.put("speech_ended_at", epochMsToLocalDateTime(result.endMs()).toString());
            String json = objectMapper.writeValueAsString(body);

            // 보안상 원문(text)은 로그에 남기지 않고 길이만 남긴다.
            log.info(
                    "[STT/최종결과] sessionId={}, speakerId={}, meetingId={}, textLength={}",
                    result.sessionId(),
                    speakerDiscordId,
                    meetingId,
                    text.length());
            log.info(
                    "[STT/최종텍스트] sessionId={}, speakerId={}, meetingId={}, text={}",
                    result.sessionId(),
                    speakerDiscordId,
                    meetingId,
                    text);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(internalBaseUrl + "/internal/v1/speech"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));

            // 내부 API가 키 인증을 쓰는 경우 헤더를 붙인다.
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                requestBuilder.header("X-Internal-Api-Key", internalApiKey);
            }

            httpClient
                    .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            log.warn(
                                    "[전송/실패] sessionId={}, speakerId={}, meetingId={}",
                                    result.sessionId(),
                                    speakerDiscordId,
                                    meetingId,
                                    throwable);
                            return;
                        }

                        if (response.statusCode() / 100 != 2) {
                            log.warn(
                                    "[전송/비정상응답] sessionId={}, speakerId={}, meetingId={}, status={}, body={}",
                                    result.sessionId(),
                                    speakerDiscordId,
                                    meetingId,
                                    response.statusCode(),
                                    response.body());
                            return;
                        }

                        // 보안상 원문(text)은 로그에 남기지 않는다.
                        log.info(
                                "[전송/성공] sessionId={}, speakerId={}, meetingId={}, textLength={}",
                                result.sessionId(),
                                speakerDiscordId,
                                meetingId,
                                text.length());
                        log.info(
                                "[전송/응답바디] sessionId={}, speakerId={}, meetingId={}, body={}",
                                result.sessionId(),
                                speakerDiscordId,
                                meetingId,
                                response.body());
                        logAiAnswerStatus(result.sessionId(), text, response.body());
                    });
        } catch (Exception exception) {
            log.warn(
                    "[전송/직렬화실패] sessionId={}, speakerId={}, meetingId={}",
                    result.sessionId(),
                    speakerDiscordId,
                    meetingId,
                    exception);
        }
    }

    @Override
    public void onError(String sessionId, Throwable throwable) {
        cancelPendingPartialTask();
        clearLiveCaptionMessage();
        log.warn(
                "[STT/오류] sessionId={}, speakerId={}, meetingId={}", sessionId, speakerDiscordId, meetingId, throwable);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeSpeakerName(String rawSpeakerName, String speakerId) {
        if (rawSpeakerName == null || rawSpeakerName.isBlank()) {
            return "user-" + speakerId;
        }
        return rawSpeakerName.trim();
    }

    private void upsertLiveCaption(String text, boolean isFinal) {
        if (captionChannel == null || text == null || text.isBlank()) {
            return;
        }

        desiredCaptionContent.set(formatCaption(text, isFinal));
        desiredCaptionFinal.set(isFinal);
        syncLiveCaption();
    }

    private void schedulePartialCaptionUpdate(String partialText) {
        long version = pendingPartialVersion.incrementAndGet();
        pendingPartialText.set(partialText);
        ScheduledFuture<?> previous = pendingPartialTask.getAndSet(CAPTION_DEBOUNCE_EXECUTOR.schedule(
                () -> {
                    if (version != pendingPartialVersion.get()) {
                        return;
                    }
                    String latest = pendingPartialText.getAndSet("");
                    if (latest != null && !latest.isBlank()) {
                        upsertLiveCaption(latest, false);
                    }
                    pendingPartialTask.set(null);
                },
                CAPTION_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS));

        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void cancelPendingPartialTask() {
        ScheduledFuture<?> pending = pendingPartialTask.getAndSet(null);
        if (pending != null) {
            pending.cancel(false);
        }
        pendingPartialText.set("");
        pendingPartialVersion.incrementAndGet();
    }

    private void syncLiveCaption() {
        String messageId = liveCaptionMessageId.get();
        if (messageId == null) {
            if (!captionMessageCreating.compareAndSet(false, true)) {
                return;
            }
            String messageContent = desiredCaptionContent.get();
            captionChannel
                    .sendMessage(messageContent)
                    .queue(
                            message -> {
                                liveCaptionMessageId.set(message.getId());
                                renderedCaptionContent.set(messageContent);
                                captionMessageCreating.set(false);
                                if (!messageContent.equals(desiredCaptionContent.get())) {
                                    syncLiveCaption();
                                    return;
                                }
                                if (desiredCaptionFinal.get()) {
                                    clearLiveCaptionState();
                                }
                            },
                            throwable -> {
                                captionMessageCreating.set(false);
                                log.warn(
                                        "[STT/자막메시지생성실패] speakerId={}, meetingId={}",
                                        speakerDiscordId,
                                        meetingId,
                                        throwable);
                            });
            return;
        }

        String desiredContent = desiredCaptionContent.get();
        String renderedContent = renderedCaptionContent.get();
        if (desiredContent.equals(renderedContent)) {
            return;
        }
        if (!captionEditInFlight.compareAndSet(false, true)) {
            return;
        }

        captionChannel
                .editMessageById(messageId, desiredContent)
                .queue(
                        message -> {
                            renderedCaptionContent.set(desiredContent);
                            captionEditInFlight.set(false);
                            if (!desiredContent.equals(desiredCaptionContent.get())) {
                                syncLiveCaption();
                                return;
                            }
                            if (desiredCaptionFinal.get()) {
                                clearLiveCaptionState();
                            }
                        },
                        throwable -> {
                            captionEditInFlight.set(false);
                            log.warn(
                                    "[STT/자막메시지수정실패] speakerId={}, meetingId={}, messageId={}",
                                    speakerDiscordId,
                                    meetingId,
                                    messageId,
                                    throwable);
                        });
    }

    private String formatCaption(String text, boolean isFinal) {
        return "[" + speakerName + "] " + text + (isFinal ? " [final]" : "");
    }

    private void clearLiveCaptionState() {
        liveCaptionMessageId.set(null);
        desiredCaptionContent.set("");
        renderedCaptionContent.set("");
        desiredCaptionFinal.set(false);
        captionMessageCreating.set(false);
        captionEditInFlight.set(false);
    }

    private void clearLiveCaptionMessage() {
        String messageId = liveCaptionMessageId.get();
        if (captionChannel != null && messageId != null) {
            captionChannel
                    .deleteMessageById(messageId)
                    .queue(
                            ignored -> {},
                            throwable -> log.debug(
                                    "[STT/자막메시지삭제실패] speakerId={}, meetingId={}, messageId={}",
                                    speakerDiscordId,
                                    meetingId,
                                    messageId,
                                    throwable));
        }
        clearLiveCaptionState();
    }

    private void logAiAnswerStatus(String sessionId, String finalText, String responseBody) {
        boolean wakeWordDetected = containsWakeWord(finalText);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode aiAnswerNode = root.path("data").path("ai_answer");
            boolean hasAiAnswer = !aiAnswerNode.isMissingNode()
                    && !aiAnswerNode.isNull()
                    && !aiAnswerNode.asText().isBlank();
            int aiAnswerLength = hasAiAnswer ? aiAnswerNode.asText().length() : 0;
            log.info(
                    "[AI/응답체크] sessionId={}, speakerId={}, meetingId={}, wakeWordDetected={}, hasAiAnswer={}, aiAnswerLength={}",
                    sessionId,
                    speakerDiscordId,
                    meetingId,
                    wakeWordDetected,
                    hasAiAnswer,
                    aiAnswerLength);
        } catch (Exception parseException) {
            log.warn(
                    "[AI/응답체크실패] sessionId={}, speakerId={}, meetingId={}, wakeWordDetected={}",
                    sessionId,
                    speakerDiscordId,
                    meetingId,
                    wakeWordDetected,
                    parseException);
        }
    }

    private static LocalDateTime epochMsToLocalDateTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private boolean containsWakeWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String wakeWord : WAKE_WORDS) {
            if (text.contains(wakeWord)) {
                return true;
            }
        }
        return false;
    }

    private static final class CaptionDebounceThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "stt-caption-debounce");
            thread.setDaemon(true);
            return thread;
        }
    }
}
