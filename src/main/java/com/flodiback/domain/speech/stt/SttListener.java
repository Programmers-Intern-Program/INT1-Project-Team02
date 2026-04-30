package com.flodiback.domain.speech.stt;

public interface SttListener {
    //  STT 결과가 준비되었을 때 호출되는 콜백 메서드입니다.
    void onResult(SttResult result);

    // onError는 선택적이므로, 구현체에서 필요에 따라 오버라이드할 수 있도록 기본 구현을 제공합니다.
    default void onError(String sessionId, Throwable throwable) {}
}
