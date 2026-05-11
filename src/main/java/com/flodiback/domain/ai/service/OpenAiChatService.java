package com.flodiback.domain.ai.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import com.flodiback.global.client.OpenAiChatClient;
import com.flodiback.global.config.AiProviderCondition.OpenAiProviderCondition;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Conditional(OpenAiProviderCondition.class)
public class OpenAiChatService implements AiChatService {

    private final OpenAiChatClient openAiChatClient;

    @Override
    public String generateAnswer(String systemPrompt, String userQuestion) {
        return openAiChatClient.chat(systemPrompt, userQuestion);
    }
}
