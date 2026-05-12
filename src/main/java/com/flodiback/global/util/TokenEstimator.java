package com.flodiback.global.util;

public final class TokenEstimator {

    private TokenEstimator() {}

    /**
     * 한글 음절은 BPE 토크나이저에서 약 2토큰, 그 외 문자는 4자당 1토큰으로 추정합니다.
     * rolling summary 임계치 판단용이므로 과소 추정보다 과대 추정을 택합니다.
     */
    public static int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        long koreanChars = text.chars().filter(c -> c >= 0xAC00 && c <= 0xD7A3).count();
        long otherChars = text.length() - koreanChars;
        return Math.max(1, (int) (koreanChars * 2 + otherChars / 4));
    }
}
