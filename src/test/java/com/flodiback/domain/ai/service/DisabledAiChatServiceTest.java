package com.flodiback.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.flodiback.global.exception.ServiceException;

class DisabledAiChatServiceTest {

    private final AiChatService aiChatService = new DisabledAiChatService();

    @Test
    void generateShortAnswer_throwsServiceException_whenAiChatIsDisabled() {
        assertThatThrownBy(() -> aiChatService.generateShortAnswer("system prompt", "user question"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("503-1")
                .hasMessageContaining("AI chat service is disabled.");
    }

    @Test
    void generateSummary_throwsServiceException_whenAiChatIsDisabled() {
        assertThatThrownBy(() -> aiChatService.generateSummary("system prompt", "user question"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("503-1")
                .hasMessageContaining("AI chat service is disabled.");
    }
}
