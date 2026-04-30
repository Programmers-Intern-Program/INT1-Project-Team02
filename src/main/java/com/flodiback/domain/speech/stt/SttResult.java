package com.flodiback.domain.speech.stt;

public record SttResult(
        // 어느 세션 결과인지 식별
        String sessionId,
        // 화자 ID 구분
        String speakerId,
        // 인식된 텍스트 본문
        String text,
        // 이 결과가 최종 결과인지 여부 (중간 결과는 isFinal=false, 최종 결과는 isFinal=true) 매우 중요
        // 보통 final 일때만 회의록에 저장되고, final이 아닌 중간 결과는 UI 업데이트 등에 활용됩니다.
        boolean isFinal,
        // 텍스트 시작 시점 (ms 단위)
        long startMs,
        // 텍스트 구간 끝 시각  (ms 단위)
        long endMs,
        // 인식 신뢰도
        Float confidence) {}
