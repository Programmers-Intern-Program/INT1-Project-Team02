package com.flodiback.domain.speech.stt.provider.openai;

import com.flodiback.domain.speech.stt.SttListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

//
public class OpenAiSttSessionState {
    private final String sessionId;
    private final String speakerId;
    private final SttListener listener;

    // 관측용 누적 PCM 바이트 수 (실제 OpenAI API 연동 시에는 API 호출 시점에 따라 별도 관리할 수도 있음)
    // AtomicLong은 여러 스레드에서 안전하게 업데이트할 수 있도록 해줍니다.
    private final AtomicLong sentPcmBytes = new AtomicLong();

    //endSession 호출 여부 플래그 (중복 endSession 호출 방지 및 세션 종료 상태 관리에 활용)
    private final AtomicBoolean endRequested = new AtomicBoolean(false);
    // item_id 별 delta 누적 버퍼
    private final Map<String, StringBuilder> partialByItemId = new ConcurrentHashMap<>();

    OpenAiSttSessionState(String sessionId, String speakerId, SttListener listener) {
        this.sessionId = sessionId;
        this.speakerId = speakerId;
        this.listener = listener;
    }

    String sessionId() { return sessionId;}
    String speakerId() { return speakerId;}
    SttListener sttListener() { return listener; }

    void addSentPcmBytes(long bytes) {
        sentPcmBytes.addAndGet(bytes);
    }

    long sentPcmBytes() { return sentPcmBytes.get(); }

    void markEndRequested() { endRequested.set(true); }

    boolean isEndRequested() { return endRequested.get(); }

    // partialByItemId 맵에서 itemId에 해당하는 StringBuilder를 가져와 delta를 append한 후 전체 누적 텍스트를 반환
    String appendDelta(String itemId, String delta) {
        return partialByItemId
                .computeIfAbsent(itemId, ignored -> new StringBuilder())
                .append(delta)
                .toString();
    }

    // 완성된 문장이 전달되면 itemId에 해당하는 누적 텍스트를 제거하고 완성된 텍스트를 반환
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
