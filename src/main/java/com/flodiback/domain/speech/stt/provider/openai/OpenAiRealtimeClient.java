package com.flodiback.domain.speech.stt.provider.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// OpenAI Realtime STT 엔진과의 통신을 담당하는 클라이언트 클래스
// 실제로 OpenAI Realtime API와의 WebSocket 연결 관리, 메시지 송수신, 세션 상태 업데이트 등을 담당할 예정이며,
// OpenAiRealtimeEventHandler와 OpenAiPcmConverter를 활용하여 실시간 음성 인식 기능을 구현할 예정
final class OpenAiRealtimeClient {
    private static final Logger log =  LoggerFactory.getLogger(OpenAiRealtimeClient.class);

    // 새로운 stt세션을 열고, OpenAI Realtime API와의 WebSocket 연결을 설정하는 메서드
    void openSession(OpenAiSttSessionState session, OpenAiRealtimeEventHandler eventHandler) throws Exception {
        // TODO:
        // - OPENAI_API_KEY / WS URL 읽기
        // - WebSocket 연결
        // - session.update 전송
        log.debug("openSession TODO. sessionId={}", session.sessionId());
    }

    // 세션이 시작된 후, PCM 오디오 데이터를 OpenAI Realtime API로 전송하는 메서드
    void appendAudio(OpenAiSttSessionState session, byte[] pcm16le, long timestampMs) throws Exception {
        // TODO:
        // - input_audio_buffer.append 전송
        log.debug("appendAudio TODO. sessionId={}, bytes={}, ts={}",
                session.sessionId(), pcm16le.length, timestampMs);
    }

    // 세션이 종료될 때, OpenAI Realtime API에 세션 종료를 알리고, 최종 결과를 수신한 후 세션을 닫는 메서드
    void commitAndClose(OpenAiSttSessionState session) throws Exception {
        // TODO:
        // - input_audio_buffer.commit 전송
        // - completed 이벤트 수신 후 close
        log.debug("commitAndClose TODO. sessionId={}, sentBytes={}",
                session.sessionId(), session.sentPcmBytes());
    }

    // 예외 발생 시 조용히 세션을 닫는 유틸리티 메서드, 실제로는 WebSocket 연결 종료 및 리소스 정리를 담당할 예정
    void closeSessionQuietly(OpenAiSttSessionState session) {
        try {
            commitAndClose(session);
        } catch (Exception ignored) {
            // no-op
        }
    }
}
