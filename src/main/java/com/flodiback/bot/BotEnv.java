package com.flodiback.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 간단한 .env fallback 유틸.
 *
 * <p>우선순위: 시스템 환경변수(System.getenv) > 루트 .env 파일
 */
public final class BotEnv {
    // 클래스 로드시 한 번만 .env를 파싱해 메모리에 보관한다.
    private static final Map<String, String> dotEnvValues = loadDotEnv();

    private BotEnv() {}

    public static String get(String key) {
        // 1순위: 시스템 환경변수
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        // 2순위: .env 파일 값
        return dotEnvValues.get(key);
    }

    public static String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new ConcurrentHashMap<>();
        Path dotEnv = Path.of(".env");
        if (!Files.exists(dotEnv)) {
            // .env가 없어도 예외를 던지지 않고 빈 맵으로 처리한다.
            return values;
        }

        try {
            for (String line : Files.readAllLines(dotEnv, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    // 빈 줄/주석 줄 무시
                    continue;
                }
                int split = trimmed.indexOf('=');
                if (split <= 0) {
                    // KEY=VALUE 형태가 아니면 무시
                    continue;
                }
                String key = trimmed.substring(0, split).trim();
                String value = trimmed.substring(split + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException ignored) {
            // 파일 읽기 실패 시에도 앱 기동은 계속 가능하도록 빈 값 반환
            return values;
        }
        return values;
    }
}
