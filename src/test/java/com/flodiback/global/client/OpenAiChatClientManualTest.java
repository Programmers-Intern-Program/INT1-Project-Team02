package com.flodiback.global.client;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * $env:OPENAI_API_KEY='your_openai_api_key'
 * $env:OPENAI_CHAT_MODEL='gpt-4o-mini'
 * ./gradlew test --tests "com.flodiback.global.client.OpenAiChatClientManualTest"
 */
@Disabled("수동 실행 전용 — 토큰 소모 주의. 실행 시 @Disabled 제거")
class OpenAiChatClientManualTest {

    @Test
    void chat_responseCheck() {
        OpenAiChatClient openAiChatClient = new OpenAiChatClient(
                env("OPENAI_API_KEY", null),
                env("OPENAI_CHAT_MODEL", "gpt-4o-mini"),
                env("OPENAI_CHAT_BASE_URL", "https://api.openai.com/v1"),
                Long.parseLong(env("OPENAI_CHAT_TIMEOUT_MS", "30000")),
                Integer.parseInt(env("OPENAI_CHAT_MAX_RETRIES", "0")));
        String systemPrompt = "You are a concise Korean assistant.";
        String userPrompt = "1 + 1은 뭐야?";

        String response = openAiChatClient.chat(systemPrompt, userPrompt);

        System.out.println("=== OpenAI chat() response ===");
        System.out.println(response);
    }

    private String env(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            if (defaultValue == null) {
                throw new IllegalStateException(name + " is required.");
            }
            return defaultValue;
        }
        return value;
    }
}
