package com.flodiback.domain.speech.stt.provider.openai;

import com.flodiback.domain.speech.stt.SttResult;

// OpenAISTT에서 음성 인식 API에서 이벤트가 날아왔을때 중간 결과나, 최종 결과를 리스너에 전달하는 역할
final class OpenAiRealtimeEventHandler {
    // TODO: startMS, endMS, confidence 등도 OpenAI Realtime API에서 제공하는 정보가 있다면
    //  세션 상태에서 관리해서 SttResult에 포함해서 전달하도록 개선 필요, 또한 초기값이 아니라 타임스탬프 계산 로직도 필요할 수 있음


    //실시간에서 음성을 텍스트로 변환할때 완성된 문장이 아닌, 부분적인 결과를 처리하는 역할
    void onDelta(OpenAiSttSessionState session, String itemId, String delta) {
        if (itemId == null || delta == null) {
            return;
        }

        String cumulative = session.appendDelta(itemId, delta);

        session.sttListener().onResult(new SttResult(
                session.sessionId(),
                session.speakerId(),
                cumulative,
                false,
                0L,
                0L,
                null
        ));
    }
    // 완성된 문장이 전달되었을 때, 세션 상태에서 해당 itemId에 누적된 텍스트를 제거하고, 최종 결과로 리스너에 전달하는 역할
    void onCompleted(OpenAiSttSessionState session, String itemId, String transcript) {
        String finalText = session.takeCompletedText(itemId, transcript);

        session.sttListener().onResult(new SttResult(
                session.sessionId(),
                session.speakerId(),
                finalText,
                true,
                0L,
                0L,
                null));
    }
    // 오류가 발생했을 때, 세션의 리스너에 오류 정보를 전달하는 역할
    void onError(OpenAiSttSessionState session, Throwable throwable) {
        session.sttListener().onError(session.sessionId(), throwable);
    }


}
