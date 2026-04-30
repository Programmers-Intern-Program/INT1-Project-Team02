package com.flodiback.domain.speech.stt.provider.openai;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenAI Realtime WebSocket 수신 이벤트 처리기
 */
final class OpenAiRealtimeWsListener implements WebSocket.Listener {
    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeWsListener.class);

    private final OpenAiSttSessionState session;
    private final OpenAiRealtimeEventHandler eventHandler;
    private final ObjectMapper objectMapper;
    private final StringBuilder frameBuffer = new StringBuilder();

    OpenAiRealtimeWsListener(
            OpenAiSttSessionState session, OpenAiRealtimeEventHandler eventHandler, ObjectMapper objectMapper) {
        this.session = session;
        this.eventHandler = eventHandler;
        this.objectMapper = objectMapper;
    }

    // 자바의 웹소켓은 백프레셔
    // (백프레셔는 소비자가 생산자에게 데이터를 보내기 전에 준비가 되었는지 확인하는 메커니즘)를 지원하므로,
    // 메시지를 받을 준비가 되었음을 알립니다.
    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        frameBuffer.append(data);
        if (!last) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        String message = frameBuffer.toString();
        frameBuffer.setLength(0);

        try {
            handleServerEvent(message);
        } catch (Exception exception) {
            eventHandler.onError(session, exception);
        }

        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("OpenAI ws closed. sessionId={}, statusCode={}, reason={}", session.sessionId(), statusCode, reason);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        eventHandler.onError(session, error);
    }

    private void handleServerEvent(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        String type = text(root, "type");
        if (type == null) {
            return;
        }

        switch (type) {
            case "conversation.item.input_audio_transcription.completed" -> {
                String itemId = text(root, "item_id");
                String transcript = text(root, "transcript");
                eventHandler.onCompleted(session, itemId, transcript);
            }
            case "conversation.item.input_audio_transcription.delta" -> {
                String itemId = text(root, "item_id");
                String delta = text(root, "delta");
                eventHandler.onDelta(session, itemId, delta);
            }
            case "error" -> {
                String message = root.path("error").path("message").asText("unknown realtime error");
                eventHandler.onError(session, new RuntimeException(message));
            }
            default -> {
                // no-op
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
