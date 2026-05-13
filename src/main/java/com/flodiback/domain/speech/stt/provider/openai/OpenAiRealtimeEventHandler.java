package com.flodiback.domain.speech.stt.provider.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flodiback.domain.speech.stt.SttResult;

/**
 * OpenAI Realtime 이벤트를 STT 도메인 이벤트(SttResult)로 변환한다.
 */
final class OpenAiRealtimeEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeEventHandler.class);
    private static final OpenAiRealtimeRateLimitGate SHARED_RATE_LIMIT_GATE = new OpenAiRealtimeRateLimitGate();
    // OpenAI Realtime에는 24kHz mono PCM16LE를 보낸다.
    // 24,000 samples/sec * 2 bytes = 48 bytes/ms 이므로 전송 바이트로 길이를 역산할 수 있다.
    private static final long REALTIME_PCM_BYTES_PER_MS = 48L;

    private final OpenAiRealtimeRateLimitGate rateLimitGate;

    OpenAiRealtimeEventHandler() {
        this(SHARED_RATE_LIMIT_GATE);
    }

    OpenAiRealtimeEventHandler(OpenAiRealtimeRateLimitGate rateLimitGate) {
        this.rateLimitGate = rateLimitGate;
    }

    /**
     * 중간 결과(delta) 이벤트 처리.
     * - item_id별로 누적 문자열을 만든 뒤
     * - isFinal=false 결과로 리스너에 전달한다.
     */
    void onDelta(OpenAiSttSessionState session, String itemId, String delta) {
        if (itemId == null || delta == null) {
            return;
        }

        String cumulative = session.appendDelta(itemId, delta);
        log.debug(
                "[OPENAI/중간전사] sessionId={}, itemId={}, deltaLength={}, cumulativeLength={}",
                session.sessionId(),
                itemId,
                delta.length(),
                cumulative.length());

        long sentPcmBytes = session.sentPcmBytes();
        session.sttListener()
                .onResult(new SttResult(
                        session.sessionId(),
                        session.speakerId(),
                        cumulative,
                        false,
                        startMs(session),
                        endMs(session),
                        sentPcmBytes,
                        audioDurationMs(sentPcmBytes),
                        null));
    }

    /**
     * 최종 결과(completed) 이벤트 처리.
     * - transcript가 비어 있으면 delta 누적 fallback 사용
     * - isFinal=true로 리스너에 전달
     * - commit 대기 future 완료 신호 전달
     */
    void onCompleted(OpenAiSttSessionState session, String itemId, String transcript) {
        String finalText = session.takeCompletedText(itemId, transcript);
        log.info(
                "[OPENAI/최종전사] sessionId={}, itemId={}, textLength={}",
                session.sessionId(),
                itemId,
                finalText.length());

        long sentPcmBytes = session.sentPcmBytes();
        session.sttListener()
                .onResult(new SttResult(
                        session.sessionId(),
                        session.speakerId(),
                        finalText,
                        true,
                        startMs(session),
                        endMs(session),
                        sentPcmBytes,
                        audioDurationMs(sentPcmBytes),
                        null));

        session.markCompleted();
    }

    private long audioDurationMs(long sentPcmBytes) {
        if (sentPcmBytes <= 0) {
            return 0L;
        }
        return sentPcmBytes / REALTIME_PCM_BYTES_PER_MS;
    }

    private long startMs(OpenAiSttSessionState session) {
        long firstAudioAtMs = session.firstAudioAtMs();
        return firstAudioAtMs > 0 ? firstAudioAtMs : System.currentTimeMillis();
    }

    private long endMs(OpenAiSttSessionState session) {
        long lastAudioAtMs = session.lastAudioAtMs();
        return lastAudioAtMs > 0 ? lastAudioAtMs : startMs(session);
    }

    /**
     * 에러 이벤트 처리.
     * - 리스너에 에러 전달
     * - commit 대기 future를 실패로 종료
     */
    void onError(OpenAiSttSessionState session, Throwable throwable) {
        rateLimitGate.recordIfRateLimited(throwable);
        log.warn("[OPENAI/이벤트오류] sessionId={}", session.sessionId(), throwable);
        session.sttListener().onError(session.sessionId(), throwable);
        session.markFailed(throwable);
    }

    OpenAiRealtimeRateLimitGate rateLimitGate() {
        return rateLimitGate;
    }
}
