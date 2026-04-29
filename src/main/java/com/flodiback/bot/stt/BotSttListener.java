package com.flodiback.bot.stt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.bot.BotEnv;
import com.flodiback.domain.speech.dto.InternalSpeechRequest;
import com.flodiback.domain.speech.stt.SttListener;
import com.flodiback.domain.speech.stt.SttResult;

/**
 * STT 결과 소비자.
 *
 * <p>역할:
 * - STT 결과 중 최종본(isFinal=true)만 선택
 * - 내부 API `/internal/v1/speech`로 전달
 */
public class BotSttListener implements SttListener {
    private static final Logger log = LoggerFactory.getLogger(BotSttListener.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final long meetingId;
    private final String speakerDiscordId;
    private final String speakerName;
    private final String internalBaseUrl;
    private final String internalApiKey;

    public BotSttListener(long meetingId, String speakerDiscordId, String speakerName) {
        this.meetingId = meetingId;
        this.speakerDiscordId = speakerDiscordId;
        this.speakerName = normalizeSpeakerName(speakerName, speakerDiscordId);
        this.internalBaseUrl = normalizeBaseUrl(BotEnv.getOrDefault("INTERNAL_API_BASE_URL", "http://localhost:8080"));
        this.internalApiKey = BotEnv.get("INTERNAL_API_KEY");
    }

    @Override
    public void onResult(SttResult result) {
        // 중간 결과(delta)는 저장 API로 보내지 않는다.
        if (!result.isFinal()) {
            return;
        }

        // 최종 텍스트가 비어 있으면 무시한다.
        String text = result.text();
        if (text == null || text.isBlank()) {
            return;
        }

        try {
            InternalSpeechRequest body =
                    new InternalSpeechRequest(meetingId, speakerDiscordId, speakerName, text, LocalDateTime.now());

            String json = objectMapper.writeValueAsString(body);

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
                                    "Failed to POST speech. sessionId={}, speakerId={}, meetingId={}",
                                    result.sessionId(),
                                    speakerDiscordId,
                                    meetingId,
                                    throwable);
                            return;
                        }

                        if (response.statusCode() / 100 != 2) {
                            log.warn(
                                    "Speech POST non-2xx. sessionId={}, speakerId={}, meetingId={}, status={}, body={}",
                                    result.sessionId(),
                                    speakerDiscordId,
                                    meetingId,
                                    response.statusCode(),
                                    response.body());
                            return;
                        }

                        // 보안상 원문(text)은 로그에 남기지 않는다.
                        log.info(
                                "Speech POST success. sessionId={}, speakerId={}, meetingId={}, textLength={}",
                                result.sessionId(),
                                speakerDiscordId,
                                meetingId,
                                text.length());
                    });
        } catch (Exception exception) {
            log.warn(
                    "Failed to serialize/send speech. sessionId={}, speakerId={}, meetingId={}",
                    result.sessionId(),
                    speakerDiscordId,
                    meetingId,
                    exception);
        }
    }

    @Override
    public void onError(String sessionId, Throwable throwable) {
        log.warn(
                "STT error. sessionId={}, speakerId={}, meetingId={}",
                sessionId,
                speakerDiscordId,
                meetingId,
                throwable);
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
}
