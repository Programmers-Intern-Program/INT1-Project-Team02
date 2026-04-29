package com.flodiback.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.flodiback.global.exception.ServiceException;

class DisabledAiChatServiceTest {

    private final AiChatService aiChatService = new DisabledAiChatService();

    @Test
    void generateAnswer_throwsServiceException_whenAiChatIsDisabled() {
        assertThatThrownBy(() -> aiChatService.generateAnswer("system prompt", "user question"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("503-1")
                .hasMessageContaining("GLM 채팅 서비스가 비활성화되어 있습니다.");
    }
}
