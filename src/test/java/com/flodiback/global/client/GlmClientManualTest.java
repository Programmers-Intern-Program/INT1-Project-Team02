package com.flodiback.global.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * GlmClient 수동 실행 테스트.
 *
 * <p>실제 Z.AI API를 호출하므로 토큰이 소모됩니다.
 * 자동 빌드·CI에서는 실행되지 않으며, 검증이 필요할 때만 @Disabled를 제거하고 실행하세요.
 *
 * <p>실행 전 로컬 .env 파일에 GLM_API_KEY가 설정되어 있어야 합니다.
 */
// @Disabled("수동 실행 전용 — 토큰 소모 주의. 실행 시 @Disabled 제거")
@SpringBootTest
class GlmClientManualTest {

    @Autowired
    private GlmClient glmClient;

    @Test
    void chat_응답확인() {
        String systemPrompt = "당신은 친절한 어시스턴트입니다. 한국어로 답하세요.";
        String userPrompt = "1 + 1은 뭐야?";

        String response = glmClient.chat(systemPrompt, userPrompt);

        System.out.println("=== chat() 응답 ===");
        System.out.println(response);
    }
}
