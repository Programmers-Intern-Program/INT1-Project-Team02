package com.flodiback.domain.ai.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import com.flodiback.global.client.GlmClient;
import com.flodiback.global.config.AiProviderCondition.GlmProviderCondition;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Conditional(GlmProviderCondition.class)
public class GlmAiChatService implements AiChatService {

    private final GlmClient glmClient;

    @Override
    public String generateShortAnswer(String systemPrompt, String userQuestion) {
        return glmClient.chat(systemPrompt, userQuestion);
    }

    @Override
    public String generateSummary(String systemPrompt, String userQuestion) {
        return glmClient.chat(systemPrompt, userQuestion);
    }
}
