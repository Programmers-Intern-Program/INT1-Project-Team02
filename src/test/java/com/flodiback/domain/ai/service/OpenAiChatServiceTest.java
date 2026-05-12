package com.flodiback.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.flodiback.global.client.OpenAiChatClient;

@ExtendWith(MockitoExtension.class)
class OpenAiChatServiceTest {

    @Mock
    private OpenAiChatClient openAiChatClient;

    @InjectMocks
    private OpenAiChatService openAiChatService;

    @Test
    void generateShortAnswer_delegatesToOpenAiChatClientWithTokenCap() {
        given(openAiChatClient.chat("system", "user", 256)).willReturn("answer");

        String result = openAiChatService.generateShortAnswer("system", "user");

        assertThat(result).isEqualTo("answer");
        verify(openAiChatClient).chat("system", "user", 256);
    }

    @Test
    void generateSummary_delegatesToOpenAiChatClientWithoutTokenCap() {
        given(openAiChatClient.chat("system", "user")).willReturn("summary");

        String result = openAiChatService.generateSummary("system", "user");

        assertThat(result).isEqualTo("summary");
        verify(openAiChatClient).chat("system", "user");
    }
}
