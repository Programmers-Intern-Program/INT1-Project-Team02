package com.flodiback.domain.ai.service;

public interface AiChatService {

    // 호출어/RAG 흐름이 실제 GLM SDK 구현체에 직접 의존하지 않도록 분리합니다.
    String generateAnswer(String systemPrompt, String userQuestion);
}
