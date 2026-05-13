package com.flodiback.domain.speech.stt.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

    @Test
    void onError_recordsRealtimeRateLimitCooldown() {
        AtomicLong now = new AtomicLong(1_000L);
        OpenAiRealtimeRateLimitGate rateLimitGate = new OpenAiRealtimeRateLimitGate(now::get);
        OpenAiRealtimeEventHandler rateLimitedEventHandler = new OpenAiRealtimeEventHandler(rateLimitGate);
        CapturingSttListener listener = new CapturingSttListener();
        OpenAiSttSessionState session = new OpenAiSttSessionState("session-1", "speaker-1", listener);

        rateLimitedEventHandler.onError(
                session,
                new RuntimeException(
                        "Rate limit reached for gpt-realtime on requests per day. Please try again in 1m26.4s."));

        assertThat(rateLimitGate.remainingMillis()).isEqualTo(86_900L);
        assertThat(listener.onlyError().getMessage()).contains("Rate limit reached");

        now.addAndGet(86_901L);

        assertThat(rateLimitGate.isBlocked()).isFalse();
    }

    @Test
    void appendBufferedPcmAndDrainIfDue_batchesSmallAudioFrames() {
        OpenAiSttSessionState session = new OpenAiSttSessionState("session-1", "speaker-1", new CapturingSttListener());

        assertThat(session.appendBufferedPcmAndDrainIfDue(new byte[] {1, 2}, 1_000L, 500L, 10))
                .isEmpty();
        assertThat(session.appendBufferedPcmAndDrainIfDue(new byte[] {3, 4}, 1_240L, 500L, 10))
                .isEmpty();

        assertThat(session.appendBufferedPcmAndDrainIfDue(new byte[] {5, 6}, 1_500L, 500L, 10))
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void appendBufferedPcmAndDrainIfDue_flushesWhenBufferLimitReached() {
        OpenAiSttSessionState session = new OpenAiSttSessionState("session-1", "speaker-1", new CapturingSttListener());

        assertThat(session.appendBufferedPcmAndDrainIfDue(new byte[] {1, 2}, 1_000L, 5_000L, 4))
                .isEmpty();

        assertThat(session.appendBufferedPcmAndDrainIfDue(new byte[] {3, 4}, 1_020L, 5_000L, 4))
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void drainBufferedPcm_flushesRemainingAudioBeforeCommit() {
        OpenAiSttSessionState session = new OpenAiSttSessionState("session-1", "speaker-1", new CapturingSttListener());

        assertThat(session.appendBufferedPcmAndDrainIfDue(new byte[] {1, 2}, 1_000L, 500L, 10))
                .isEmpty();

        assertThat(session.drainBufferedPcm()).containsExactly(1, 2);
        assertThat(session.drainBufferedPcm()).isEmpty();
    }

    private static final class CapturingSttListener implements SttListener {
        private final List<SttResult> results = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onResult(SttResult result) {
            results.add(result);
        }

        @Override
        public void onError(String sessionId, Throwable throwable) {
            errors.add(throwable);
        }

        private SttResult onlyResult() {
            assertThat(results).hasSize(1);
            return results.get(0);
        }

        private Throwable onlyError() {
            assertThat(errors).hasSize(1);
            return errors.get(0);
        }
    }
}
