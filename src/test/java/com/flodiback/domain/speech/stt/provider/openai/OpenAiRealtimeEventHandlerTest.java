package com.flodiback.domain.speech.stt.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.flodiback.domain.speech.stt.SttListener;
import com.flodiback.domain.speech.stt.SttResult;

class OpenAiRealtimeEventHandlerTest {

    private final OpenAiRealtimeEventHandler eventHandler = new OpenAiRealtimeEventHandler();

    @Test
    void onDelta_usesFirstAndLastAudioTimestamps() {
        CapturingSttListener listener = new CapturingSttListener();
        OpenAiSttSessionState session = new OpenAiSttSessionState("session-1", "speaker-1", listener);
        session.recordSentPcm(4_800L, 1_000L);
        session.recordSentPcm(4_800L, 1_120L);

        eventHandler.onDelta(session, "item-1", "안녕");

        SttResult result = listener.onlyResult();
        assertThat(result.isFinal()).isFalse();
        assertThat(result.startMs()).isEqualTo(1_000L);
        assertThat(result.endMs()).isEqualTo(1_120L);
        assertThat(result.sentPcmBytes()).isEqualTo(9_600L);
        assertThat(result.audioDurationMs()).isEqualTo(200L);
    }

    @Test
    void onCompleted_usesFirstAndLastAudioTimestamps() {
        CapturingSttListener listener = new CapturingSttListener();
        OpenAiSttSessionState session = new OpenAiSttSessionState("session-1", "speaker-1", listener);
        session.recordSentPcm(4_800L, 2_000L);
        session.recordSentPcm(4_800L, 2_180L);

        eventHandler.onCompleted(session, "item-1", "최종 전사");

        SttResult result = listener.onlyResult();
        assertThat(result.isFinal()).isTrue();
        assertThat(result.startMs()).isEqualTo(2_000L);
        assertThat(result.endMs()).isEqualTo(2_180L);
        assertThat(result.sentPcmBytes()).isEqualTo(9_600L);
        assertThat(result.audioDurationMs()).isEqualTo(200L);
    }

    private static final class CapturingSttListener implements SttListener {
        private final List<SttResult> results = new ArrayList<>();

        @Override
        public void onResult(SttResult result) {
            results.add(result);
        }

        @Override
        public void onError(String sessionId, Throwable throwable) {
            throw new AssertionError("Unexpected STT error", throwable);
        }

        private SttResult onlyResult() {
            assertThat(results).hasSize(1);
            return results.get(0);
        }
    }
}
