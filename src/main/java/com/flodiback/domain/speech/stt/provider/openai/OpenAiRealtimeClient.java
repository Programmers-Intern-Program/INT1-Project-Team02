package com.flodiback.domain.speech.stt.provider.openai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * OpenAI Realtime STT WebSocket 저수준 클라이언트.
 *
 * <p>역할:
 * - 세션 오픈(WebSocket 연결 + session.update)
 * - 오디오 append 전송
 * - commit 후 completed 대기 + close
 */
final class OpenAiRealtimeClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeClient.class);

    private static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    private static final String ENV_OPENAI_REALTIME_WS_URL = "OPENAI_REALTIME_WS_URL";
    private static final String ENV_OPENAI_TRANSCRIBE_MODEL = "OPENAI_TRANSCRIBE_MODEL";

    private static final String DEFAULT_TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe";
    private static final Duration COMMIT_WAIT_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * WebSocket 연결을 열고 transcription 세션 설정(session.update)을 전송한다.
     */
    void openSession(OpenAiSttSessionState session, OpenAiRealtimeEventHandler eventHandler) throws Exception {
        String apiKey = requireEnv(ENV_OPENAI_API_KEY);
        String wsUrl = normalizeTranscriptionWsUrl(requireEnv(ENV_OPENAI_REALTIME_WS_URL));

        // OpenAI Realtime 서버와 WebSocket 연결
        WebSocket webSocket = httpClient
                .newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .buildAsync(URI.create(wsUrl), new OpenAiRealtimeWsListener(session, eventHandler, objectMapper))
                .join();

        session.bindWebSocket(webSocket);

        // transcription 세션 옵션 전송
        // - input format: audio/pcm 24kHz
        // - model: env override 가능
        // - turn_detection: null -> 수동 commit 모드
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "session.update");

        ObjectNode sessionNode = root.putObject("session");
        sessionNode.put("type", "transcription");

        ObjectNode inputNode = sessionNode.putObject("audio").putObject("input");
        inputNode.putObject("format").put("type", "audio/pcm").put("rate", 24000);
        inputNode
                .putObject("transcription")
                .put("model", envOrDefault(ENV_OPENAI_TRANSCRIBE_MODEL, DEFAULT_TRANSCRIBE_MODEL));
        inputNode.putNull("turn_detection");

        sendJson(webSocket, root);

        log.info("OpenAI STT session opened. sessionId={}, speakerId={}", session.sessionId(), session.speakerId());
    }

    /**
     * PCM 청크를 Base64로 인코딩해 input_audio_buffer.append 이벤트로 전송한다.
     */
    void appendAudio(OpenAiSttSessionState session, byte[] pcm16le, long timestampMs) throws Exception {
        WebSocket webSocket = requireWebSocket(session);

        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "input_audio_buffer.append");
        event.put("audio", Base64.getEncoder().encodeToString(pcm16le));

        sendJson(webSocket, event);

        log.debug(
                "OpenAI append sent. sessionId={}, bytes={}, ts={}", session.sessionId(), pcm16le.length, timestampMs);
    }

    /**
     * 발화 종료 시 commit을 보내고 completed 신호를 잠시 기다린 뒤 연결을 닫는다.
     */
    void commitAndClose(OpenAiSttSessionState session) throws Exception {
        WebSocket webSocket = requireWebSocket(session);

        ObjectNode commit = objectMapper.createObjectNode();
        commit.put("type", "input_audio_buffer.commit");
        sendJson(webSocket, commit);

        log.info("OpenAI commit sent. sessionId={}, sentBytes={}", session.sessionId(), session.sentPcmBytes());

        // completed 이벤트를 기다렸다가 닫는다.
        // 네트워크 상태에 따라 못 받을 수도 있으므로 timeout을 둔다.
        try {
            session.completedFuture().get(COMMIT_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception timeoutOrError) {
            log.warn("OpenAI completed wait timeout/error. sessionId={}", session.sessionId(), timeoutOrError);
        }

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    /**
     * 교체/예외 케이스에서 조용히 세션을 정리한다.
     */
    void closeSessionQuietly(OpenAiSttSessionState session) {
        try {
            WebSocket webSocket = session.webSocket();
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "cleanup").join();
            }
        } catch (Exception ignored) {
            // 의도적으로 무시: cleanup 경로에서의 2차 예외는 로직을 중단시키지 않는다.
        }
    }

    private WebSocket requireWebSocket(OpenAiSttSessionState session) {
        WebSocket webSocket = session.webSocket();
        if (webSocket == null) {
            throw new IllegalStateException("WebSocket not initialized. sessionId=" + session.sessionId());
        }
        return webSocket;
    }

    private void sendJson(WebSocket webSocket, ObjectNode payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        webSocket.sendText(json, true).join();
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " environment variable is required.");
        }
        return value.trim();
    }

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private String normalizeTranscriptionWsUrl(String wsUrl) {
        if (wsUrl.contains("intent=transcription")) {
            return wsUrl;
        }

        // Current Realtime WebSocket routing creates a regular realtime session by default.
        // Transcription session.update payloads require the connection to be opened as transcription intent.
        return wsUrl + (wsUrl.contains("?") ? "&" : "?") + "intent=transcription";
    }
}
