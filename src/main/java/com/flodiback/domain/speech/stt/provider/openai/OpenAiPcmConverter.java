package com.flodiback.domain.speech.stt.provider.openai;

// JDA PCM -> OpenAi Realtime 입력을 위한 PCM 변환기
final class OpenAiPcmConverter {

    byte[] toRealtimePcm16(byte[] jdaPcm) {
        // TODO: 현재 JDA PCM이 48kHz 16-bit stereo 라고 가정 하고 그냥 통과,
        //  OpenAI Realtime이 24kHz 16-bit mono 를 요구하므로 이후 endian 정규화 구현 필요
        return jdaPcm;
    }
}
