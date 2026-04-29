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
    private final Map<String, OpenAiSttSessionState> sessions = new ConcurrentHashMap<>();
    private final OpenAiRealtimeClient realtimeClient;
    private final OpenAiPcmConverter pcmConverter;
    private final OpenAiRealtimeEventHandler eventHandler;

    // 내부에서 필요한 기본 객체들을 직접 만듦
    public OpenAiSttProvider() {
        this(new OpenAiRealtimeClient(), new OpenAiPcmConverter(), new OpenAiRealtimeEventHandler());
    }

    // 외부에서 객체를 주입할 수 있는 생성자 (테스트 용이성 향상)
    OpenAiSttProvider(
            OpenAiRealtimeClient realtimeClient,
            OpenAiPcmConverter pcmConverter,
            OpenAiRealtimeEventHandler eventHandler) {
        this.realtimeClient = Objects.requireNonNull(realtimeClient);
        this.pcmConverter = Objects.requireNonNull(pcmConverter);
        this.eventHandler = Objects.requireNonNull(eventHandler);
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public void startSession(String sessionId, String speakerId, SttListener listener) {
        SttListener nonNullListener = Objects.requireNonNull(listener);
        OpenAiSttSessionState newSession = new OpenAiSttSessionState(sessionId, speakerId, nonNullListener);

        OpenAiSttSessionState oldSession = sessions.put(sessionId, newSession);
        if (oldSession != null) {
            realtimeClient.closeSessionQuietly(oldSession);
        }

        try {
            realtimeClient.openSession(newSession, eventHandler);
        } catch (Exception exception) {
            sessions.remove(sessionId, newSession);
            nonNullListener.onError(sessionId, exception);
        }
    }

    @Override
    public void sendPcm(String sessionId, byte[] pcm16le, long timestampMs) {
        OpenAiSttSessionState session = sessions.get(sessionId);
        if (session == null || pcm16le == null || pcm16le.length == 0) {
            return;
        }
        try {
            byte[] realtimePcm = pcmConverter.toRealtimePcm16(pcm16le);
            session.addSentPcmBytes(realtimePcm.length);
            realtimeClient.appendAudio(session, realtimePcm, timestampMs);
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
            realtimeClient.commitAndClose(session);
        } catch (Exception exception) {
            session.sttListener().onError(sessionId, exception);
            realtimeClient.closeSessionQuietly(session);
        }
    }
}
