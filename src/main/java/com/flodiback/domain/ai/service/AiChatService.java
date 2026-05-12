package com.flodiback.domain.ai.service;

public interface AiChatService {

    String generateShortAnswer(String systemPrompt, String userQuestion);

    String generateSummary(String systemPrompt, String userQuestion);
}
