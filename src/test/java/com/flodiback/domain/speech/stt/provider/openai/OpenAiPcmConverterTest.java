package com.flodiback.domain.speech.stt.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiPcmConverterTest {

    private final OpenAiPcmConverter converter = new OpenAiPcmConverter();

    @Test
    void converts48kStereoPcmTo24kMonoPcm() {
        byte[] input = stereoFrames(
                new short[] {1000, 3000}, new short[] {2000, 4000}, new short[] {-1000, 1000}, new short[] {2000, 2000
                });

        byte[] output = converter.toRealtimePcm16(input);

        assertThat(output).containsExactly(littleEndianShorts((short) 2500, (short) 1000));
    }

    @Test
    void returnsEmptyWhenInputHasFewerThanTwoStereoFrames() {
        byte[] input = stereoFrames(new short[] {1000, 2000});

        byte[] output = converter.toRealtimePcm16(input);

        assertThat(output).isEmpty();
    }

    private byte[] stereoFrames(short[]... frames) {
        byte[] pcm = new byte[frames.length * 4];
        for (int i = 0; i < frames.length; i++) {
            writeShort(pcm, i * 4, frames[i][0]);
            writeShort(pcm, i * 4 + 2, frames[i][1]);
        }
        return pcm;
    }

    private byte[] littleEndianShorts(short... values) {
        byte[] pcm = new byte[values.length * 2];
        for (int i = 0; i < values.length; i++) {
            writeShort(pcm, i * 2, values[i]);
        }
        return pcm;
    }

    private void writeShort(byte[] target, int offset, short value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }
}
