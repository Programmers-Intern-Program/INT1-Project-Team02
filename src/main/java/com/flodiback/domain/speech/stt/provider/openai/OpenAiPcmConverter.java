package com.flodiback.domain.speech.stt.provider.openai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// JDA PCM -> OpenAi Realtime 입력을 위한 PCM 변환기
final class OpenAiPcmConverter {
    private static final Logger log = LoggerFactory.getLogger(OpenAiPcmConverter.class);

    private static final int PCM_16BIT_BYTES = 2;
    private static final int INPUT_CHANNELS = 2;
    private static final int INPUT_FRAME_BYTES = PCM_16BIT_BYTES * INPUT_CHANNELS;
    private static final int DOWNSAMPLE_FACTOR = 2;
    // 48k -> 24k decimation 전에 적용할 저역통과 FIR(정규화 합=64)
    // binomial 계열 계수로 구현이 단순하고 alias 억제에 유리하다.
    private static final int[] FIR_TAPS = {1, 6, 15, 20, 15, 6, 1};
    private static final int FIR_NORMALIZATION = 64;
    private static final int FIR_HALF = FIR_TAPS.length / 2;
    private static final AtomicOnce WARN_ONCE = new AtomicOnce();

    byte[] toRealtimePcm16(byte[] jdaPcm) {
        if (jdaPcm == null || jdaPcm.length == 0) {
            return new byte[0];
        }

        // JDA 디코딩 PCM은 48kHz stereo 16-bit big-endian 으로 가정한다.
        // OpenAI Realtime transcription은 24kHz mono PCM을 요구하므로:
        // 1) stereo -> mono 다운믹스
        // 2) 48k -> 24k 다운샘플
        int completeInputFrames = jdaPcm.length / INPUT_FRAME_BYTES;
        int outputFrames = completeInputFrames / DOWNSAMPLE_FACTOR;
        if (outputFrames == 0) {
            return new byte[0];
        }

        int truncatedBytes = jdaPcm.length - (completeInputFrames * INPUT_FRAME_BYTES);
        WARN_ONCE.run(() -> log.info(
                "OpenAiPcmConverter enabled. inputAssumption=48k_stereo_pcm16be, outputFormat=24k_mono_pcm16le"));
        if (truncatedBytes > 0) {
            log.debug("Ignoring trailing PCM bytes that do not fill a frame. bytes={}", truncatedBytes);
        }

        short[] mono48k = extractMono48k(jdaPcm, completeInputFrames);
        byte[] output = new byte[outputFrames * PCM_16BIT_BYTES];
        for (int outputFrameIndex = 0; outputFrameIndex < outputFrames; outputFrameIndex++) {
            int sourceIndex = outputFrameIndex * DOWNSAMPLE_FACTOR;
            short filtered = lowPassAt(mono48k, sourceIndex);
            writeLittleEndianShort(output, outputFrameIndex * PCM_16BIT_BYTES, filtered);
        }

        return output;
    }

    private short[] extractMono48k(byte[] pcm, int frameCount) {
        short[] mono = new short[frameCount];
        for (int frame = 0; frame < frameCount; frame++) {
            mono[frame] = (short) mixStereoFrameToMono(pcm, frame);
        }
        return mono;
    }

    private short lowPassAt(short[] mono, int center) {
        long accumulator = 0L;
        for (int tap = 0; tap < FIR_TAPS.length; tap++) {
            int index = center + tap - FIR_HALF;
            if (index < 0) {
                index = 0;
            } else if (index >= mono.length) {
                index = mono.length - 1;
            }
            accumulator += (long) FIR_TAPS[tap] * mono[index];
        }
        int filtered = (int) Math.round(accumulator / (double) FIR_NORMALIZATION);
        return clampToShort(filtered);
    }

    private int mixStereoFrameToMono(byte[] pcm, int frameIndex) {
        int offset = frameIndex * INPUT_FRAME_BYTES;
        short left = readBigEndianShort(pcm, offset);
        short right = readBigEndianShort(pcm, offset + PCM_16BIT_BYTES);
        return (left + right) / 2;
    }

    private short readBigEndianShort(byte[] pcm, int offset) {
        int high = pcm[offset];
        int low = pcm[offset + 1] & 0xFF;
        return (short) ((high << 8) | low);
    }

    private void writeLittleEndianShort(byte[] target, int offset, short value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private short clampToShort(int value) {
        if (value > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (value < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) value;
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
