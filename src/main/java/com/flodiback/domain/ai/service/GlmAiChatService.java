package com.flodiback.domain.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.flodiback.global.client.GlmClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "glm", name = "enabled", havingValue = "true")
public class GlmAiChatService implements AiChatService {

    private final GlmClient glmClient;

    @Override
    public String generateAnswer(String systemPrompt, String userQuestion) {
        // 실제 GLM 호출은 공용 GlmClient에 위임해 호출부가 SDK 세부 구현을 몰라도 되게 합니다.
        return glmClient.chat(systemPrompt, userQuestion);
    }
}
