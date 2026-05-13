package com.flodiback.domain.speech.stt.provider.openai;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.flodiback.domain.speech.stt.SttListener;
import com.flodiback.domain.speech.stt.SttProvider;

/**
 * STT Provider 진입점.
 *
 * 역할:
 * - 세션 수명주기 관리
 * - PCM 변환 호출
 * - Realtime 클라이언트 호출
 */
public class OpenAiSttProvider implements SttProvider {
    private static final String ENV_OPENAI_REALTIME_APPEND_FLUSH_MS = "OPENAI_REALTIME_APPEND_FLUSH_MS";
    private static final long DEFAULT_APPEND_FLUSH_MS = 500L;
    private static final long MIN_APPEND_FLUSH_MS = 100L;

    private final Map<String, OpenAiSttSessionState> sessions = new ConcurrentHashMap<>();
    private final OpenAiRealtimeClient realtimeClient;
    private final OpenAiPcmConverter pcmConverter;
    private final OpenAiRealtimeEventHandler eventHandler;
    private final OpenAiPcmDebugDumper pcmDebugDumper;
    private final long appendFlushIntervalMs;

    // 내부에서 필요한 기본 객체들을 직접 만듦
    public OpenAiSttProvider() {
        this(
                new OpenAiRealtimeClient(),
                new OpenAiPcmConverter(),
                new OpenAiRealtimeEventHandler(),
                new OpenAiPcmDebugDumper(),
                appendFlushIntervalMsFromEnv());
    }

    // 외부에서 객체를 주입할 수 있는 생성자 (테스트 용이성 향상)
    OpenAiSttProvider(
            OpenAiRealtimeClient realtimeClient,
            OpenAiPcmConverter pcmConverter,
            OpenAiRealtimeEventHandler eventHandler,
            OpenAiPcmDebugDumper pcmDebugDumper,
            long appendFlushIntervalMs) {
        this.realtimeClient = Objects.requireNonNull(realtimeClient);
        this.pcmConverter = Objects.requireNonNull(pcmConverter);
        this.eventHandler = Objects.requireNonNull(eventHandler);
        this.pcmDebugDumper = Objects.requireNonNull(pcmDebugDumper);
        this.appendFlushIntervalMs = Math.max(MIN_APPEND_FLUSH_MS, appendFlushIntervalMs);
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public void startSession(String sessionId, String speakerId, SttListener listener) {
        SttListener nonNullListener = Objects.requireNonNull(listener);
        if (eventHandler.rateLimitGate().isBlocked()) {
            IllegalStateException exception = eventHandler.rateLimitGate().blockedException(sessionId);
            nonNullListener.onError(sessionId, exception);
            throw exception;
        }

        OpenAiSttSessionState newSession = new OpenAiSttSessionState(sessionId, speakerId, nonNullListener);

        OpenAiSttSessionState oldSession = sessions.put(sessionId, newSession);
        if (oldSession != null) {
            realtimeClient.closeSessionQuietly(oldSession);
        }

        try {
            realtimeClient.openSession(newSession, eventHandler);
        } catch (Exception exception) {
            sessions.remove(sessionId, newSession);
            eventHandler.rateLimitGate().recordIfRateLimited(exception);
            nonNullListener.onError(sessionId, exception);
            throw new IllegalStateException("Failed to open OpenAI STT session. sessionId=" + sessionId, exception);
        }
    }

    @Override
    public void sendPcm(String sessionId, byte[] pcm16le, long timestampMs) {
        OpenAiSttSessionState session = sessions.get(sessionId);
        if (session == null || pcm16le == null || pcm16le.length == 0) {
            return;
        }
        if (eventHandler.rateLimitGate().isBlocked()) {
            sessions.remove(sessionId, session);
            realtimeClient.closeSessionQuietly(session);
            session.sttListener()
                    .onError(sessionId, eventHandler.rateLimitGate().blockedException(sessionId));
            return;
        }
        try {
            byte[] realtimePcm = pcmConverter.toRealtimePcm16(pcm16le);
            if (realtimePcm.length == 0) {
                return;
            }
            pcmDebugDumper.capture(sessionId, pcm16le, realtimePcm);
            session.recordSentPcm(realtimePcm.length, timestampMs);
            byte[] bufferedPcm =
                    session.appendBufferedPcmAndDrainIfDue(realtimePcm, timestampMs, appendFlushIntervalMs);
            if (bufferedPcm.length > 0) {
                realtimeClient.appendAudio(session, bufferedPcm, timestampMs);
            }
        } catch (Exception exception) {
            session.sttListener().onError(sessionId, exception);
        }
    }

    @Override
    public void endSession(String sessionId) {
        OpenAiSttSessionState session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.markEndRequested();
            byte[] remainingPcm = session.drainBufferedPcm();
            if (remainingPcm.length > 0) {
                realtimeClient.appendAudio(session, remainingPcm, session.lastAudioAtMs());
            }
            realtimeClient.commitAndClose(session);
        } catch (Exception exception) {
            session.sttListener().onError(sessionId, exception);
            realtimeClient.closeSessionQuietly(session);
        } finally {
            pcmDebugDumper.flushSession(sessionId);
        }
    }

    private static long appendFlushIntervalMsFromEnv() {
        String value = System.getenv(ENV_OPENAI_REALTIME_APPEND_FLUSH_MS);
        if (value == null || value.isBlank()) {
            return DEFAULT_APPEND_FLUSH_MS;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return DEFAULT_APPEND_FLUSH_MS;
        }
    }
}
