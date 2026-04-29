package com.flodiback.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.global.client.GlmClient;

@ExtendWith(MockitoExtension.class)
class GlmAiChatServiceTest {

    @Mock
    private GlmClient glmClient;

    @InjectMocks
    private GlmAiChatService glmAiChatService;

    @Test
    void generateAnswer_delegatesToGlmClient() {
        given(glmClient.chat("system", "user")).willReturn("answer");

        String result = glmAiChatService.generateAnswer("system", "user");

        assertThat(result).isEqualTo("answer");
        verify(glmClient).chat("system", "user");
    }
}
