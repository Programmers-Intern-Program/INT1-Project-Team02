package com.flodiback.domain.speech.stt.provider.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// JDA PCM -> OpenAi Realtime 입력을 위한 PCM 변환기
final class OpenAiPcmConverter {
    private static final Logger log = LoggerFactory.getLogger(OpenAiPcmConverter.class);

    private static final AtomicOnce WARN_ONCE = new AtomicOnce();

    byte[] toRealtimePcm16(byte[] jdaPcm) {
        // TODO: 현재 JDA PCM이 48kHz 16-bit stereo 라고 가정 하고 그냥 통과,
        //  OpenAI Realtime이 24kHz 16-bit mono 를 요구하므로 이후 endian 정규화 구현 필요
        WARN_ONCE.run(() -> log.warn(
                "OpenAiPcmConverter is pass-through mode. Implement 24k mono conversion before production use."));

        return jdaPcm;
    }

    /**
     * 단 한 번만 경고 로그를 찍기 위한 작은 유틸
     */
    private static final class AtomicOnce {
        private volatile boolean done = false;

        synchronized void run(Runnable runnable) {
            if (done) {
                return;
            }
            done = true;
            runnable.run();
        }
    }
}
