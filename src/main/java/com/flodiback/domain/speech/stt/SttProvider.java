package com.flodiback.domain.speech.stt;

public interface SttProvider {
    // 어떤 provider(OpenAi/Whisper)인지 식별
    String name();
    // STT엔진 연결/스트림 객체 생성, 결과 콜백 등록, 세션 상태 초기화 등 세션 시작에 필요한 모든 작업을 수행합니다.
    void startSession(String sessionId, String speakerId, SttListener listener);
    // 오디오 청크를 받을 때마다 반복적으로 호출되는 메서드입니다. PCM 16-bit LE 형식의 오디오 데이터를 받아서 STT 엔진으로 전달합니다.
    void sendPcm(String sessionId, byte[] pcm16le, long timestampMs);
    // 발화 종료시 호출되는 메서드
    void endSession(String sessionId);
}
