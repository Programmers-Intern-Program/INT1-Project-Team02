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
    void generateAnswer_delegatesToOpenAiChatClient() {
        given(openAiChatClient.chat("system", "user")).willReturn("answer");

        String result = openAiChatService.generateAnswer("system", "user");

        assertThat(result).isEqualTo("answer");
        verify(openAiChatClient).chat("system", "user");
    }
}
