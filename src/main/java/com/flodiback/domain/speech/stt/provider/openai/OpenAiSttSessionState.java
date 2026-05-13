package com.flodiback.domain.speech.stt.provider.openai;

import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.flodiback.domain.speech.stt.SttListener;

/**
 * OpenAI STT 세션 1개의 런타임 상태를 보관한다.
 *
 * <p>왜 필요한가?
 * - WebSocket 연결 객체
 * - delta 누적 버퍼
 * - 종료(commit) 이후 completed 이벤트 대기 신호
 * 를 세션 단위로 묶어야 start/send/end를 안전하게 처리할 수 있다.
 */
final class OpenAiSttSessionState {
    // STT 세션 식별자(guild:user:startMs 같은 규칙)
    private final String sessionId;
    // 화자 식별자(Discord user id 문자열)
    private final String speakerId;
    // 결과/에러를 전달할 콜백
    private final SttListener listener;

    // 관측용 누적 PCM 바이트(디버깅/메트릭 용도)
    private final AtomicLong sentPcmBytes = new AtomicLong();
    // 실제 STT로 전송한 첫/마지막 PCM 프레임 시각(ms)
    private final AtomicLong firstAudioAtMs = new AtomicLong();
    private final AtomicLong lastAudioAtMs = new AtomicLong();
    // endSession이 호출되었는지 상태 플래그
    private final AtomicBoolean endRequested = new AtomicBoolean(false);
    // OpenAI Realtime append 요청 수를 줄이기 위한 PCM 배치 버퍼
    private final Object bufferedPcmLock = new Object();
    private final ByteArrayOutputStream bufferedPcm = new ByteArrayOutputStream();
    private long bufferedPcmStartedAtMs = 0L;

    // OpenAI item_id별 delta 누적 버퍼
    private final Map<String, StringBuilder> partialByItemId = new ConcurrentHashMap<>();

    // commit 이후 completed 이벤트 도착을 기다리기 위한 future
    private final CompletableFuture<Void> completedFuture = new CompletableFuture<>();

    // 실제 OpenAI Realtime WebSocket 연결 객체
    private volatile WebSocket webSocket;

    OpenAiSttSessionState(String sessionId, String speakerId, SttListener listener) {
        this.sessionId = sessionId;
        this.speakerId = speakerId;
        this.listener = listener;
    }

    String sessionId() {
        return sessionId;
    }

    String speakerId() {
        return speakerId;
    }

    SttListener sttListener() {
        return listener;
    }

    void recordSentPcm(long bytes, long timestampMs) {
        sentPcmBytes.addAndGet(bytes);
        long normalizedTimestampMs = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
        firstAudioAtMs.compareAndSet(0L, normalizedTimestampMs);
        lastAudioAtMs.set(normalizedTimestampMs);
    }

    long sentPcmBytes() {
        return sentPcmBytes.get();
    }

    long firstAudioAtMs() {
        return firstAudioAtMs.get();
    }

    long lastAudioAtMs() {
        return lastAudioAtMs.get();
    }

    byte[] appendBufferedPcmAndDrainIfDue(byte[] pcm, long timestampMs, long flushIntervalMs, int maxBufferedPcmBytes) {
        if (pcm == null || pcm.length == 0) {
            return new byte[0];
        }
        long normalizedTimestampMs = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
        synchronized (bufferedPcmLock) {
            if (bufferedPcmStartedAtMs == 0L) {
                bufferedPcmStartedAtMs = normalizedTimestampMs;
            }
            bufferedPcm.writeBytes(pcm);
            boolean intervalReached = normalizedTimestampMs - bufferedPcmStartedAtMs >= flushIntervalMs;
            boolean bufferLimitReached = bufferedPcm.size() >= maxBufferedPcmBytes;
            if (!intervalReached && !bufferLimitReached) {
                return new byte[0];
            }
            return drainBufferedPcmLocked();
        }
    }

    byte[] drainBufferedPcm() {
        synchronized (bufferedPcmLock) {
            return drainBufferedPcmLocked();
        }
    }

    private byte[] drainBufferedPcmLocked() {
        byte[] drained = bufferedPcm.toByteArray();
        bufferedPcm.reset();
        bufferedPcmStartedAtMs = 0L;
        return drained;
    }

    void markEndRequested() {
        endRequested.set(true);
    }

    boolean isEndRequested() {
        return endRequested.get();
    }

    void bindWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    WebSocket webSocket() {
        return webSocket;
    }

    CompletableFuture<Void> completedFuture() {
        return completedFuture;
    }

    void markCompleted() {
        completedFuture.complete(null);
    }

    void markFailed(Throwable throwable) {
        completedFuture.completeExceptionally(throwable);
    }

    /**
     * delta 텍스트를 item_id 기준으로 누적하고 누적 문자열을 반환한다.
     */
    String appendDelta(String itemId, String delta) {
        return partialByItemId
                .computeIfAbsent(itemId, ignored -> new StringBuilder())
                .append(delta)
                .toString();
    }

    /**
     * completed 이벤트가 오면 item_id 누적 버퍼를 비우고 최종 텍스트를 반환한다.
     * completed transcript가 비어 있을 경우 delta 누적값을 fallback으로 사용한다.
     */
    String takeCompletedText(String itemId, String completedTranscript) {
        if (completedTranscript != null && !completedTranscript.isBlank()) {
            if (itemId != null) {
                partialByItemId.remove(itemId);
            }
            return completedTranscript;
        }

        if (itemId == null) {
            return "";
        }

        StringBuilder stringBuilder = partialByItemId.remove(itemId);
        return stringBuilder == null ? "" : stringBuilder.toString();
    }
}
