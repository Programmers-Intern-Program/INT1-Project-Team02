package com.flodiback.domain.ai.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import com.flodiback.global.config.AiProviderCondition.DisabledProviderCondition;
import com.flodiback.global.exception.ServiceException;

@Service
@Conditional(DisabledProviderCondition.class)
public class DisabledAiChatService implements AiChatService {

    @Override
    public String generateShortAnswer(String systemPrompt, String userQuestion) {
        throw disabledException();
    }

    @Override
    public String generateSummary(String systemPrompt, String userQuestion) {
        throw disabledException();
    }

    private ServiceException disabledException() {
        return new ServiceException("503-1", "AI chat service is disabled.");
    }
}
