package com.flodiback.domain.speech.stt.provider.openai;

import com.flodiback.domain.speech.stt.SttResult;

/**
 * OpenAI Realtime 이벤트를 STT 도메인 이벤트(SttResult)로 변환한다.
 */
final class OpenAiRealtimeEventHandler {

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

        session.sttListener()
                .onResult(new SttResult(session.sessionId(), session.speakerId(), cumulative, false, 0L, 0L, null));
    }

    /**
     * 최종 결과(completed) 이벤트 처리.
     * - transcript가 비어 있으면 delta 누적 fallback 사용
     * - isFinal=true로 리스너에 전달
     * - commit 대기 future 완료 신호 전달
     */
    void onCompleted(OpenAiSttSessionState session, String itemId, String transcript) {
        String finalText = session.takeCompletedText(itemId, transcript);

        session.sttListener()
                .onResult(new SttResult(session.sessionId(), session.speakerId(), finalText, true, 0L, 0L, null));

        session.markCompleted();
    }

    /**
     * 에러 이벤트 처리.
     * - 리스너에 에러 전달
     * - commit 대기 future를 실패로 종료
     */
    void onError(OpenAiSttSessionState session, Throwable throwable) {
        session.sttListener().onError(session.sessionId(), throwable);
        session.markFailed(throwable);
    }
}
