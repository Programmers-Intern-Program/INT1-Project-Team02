package com.flodiback.global.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;

class GlmClientTest {

    @Test
    void chat_returnsFirstChoiceContent() {
        GlmClient glmClient = new GlmClient("glm-test", params -> completion("안녕하세요."));

        String result = glmClient.chat("system", "user");

        assertThat(result).isEqualTo("안녕하세요.");
    }

    @Test
    void chat_throwsException_whenChoicesIsEmpty() {
        GlmClient glmClient = new GlmClient("glm-test", params -> emptyCompletion());

        assertThatThrownBy(() -> glmClient.chat("system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("choices");
    }

    @Test
    void chat_rethrowsException_whenGlmCallFails() {
        GlmClient glmClient = new GlmClient("glm-test", params -> {
            throw new IllegalStateException("network error");
        });

        assertThatThrownBy(() -> glmClient.chat("system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("network error");
    }

    @Test
    void chat_rethrowsIoException_whenGlmCallFails() {
        GlmClient glmClient = new GlmClient("glm-test", 15000L, 0, params -> {
            throw new OpenAIIoException("Request failed", new java.net.SocketTimeoutException("Read timed out"));
        });

        assertThatThrownBy(() -> glmClient.chat("system", "user"))
                .isInstanceOf(OpenAIIoException.class)
                .hasMessageContaining("Request failed");
    }

    @Test
    void chat_rethrowsServiceException_whenGlmReturnsErrorStatus() {
        ErrorObject error = ErrorObject.builder()
                .code("bad_request")
                .message("unsupported parameter")
                .param(Optional.of("max_completion_tokens"))
                .type("invalid_request_error")
                .build();
        UnexpectedStatusCodeException exception = UnexpectedStatusCodeException.builder()
                .statusCode(400)
                .headers(Headers.builder().build())
                .error(error)
                .build();
        GlmClient glmClient = new GlmClient("glm-test", 15000L, 0, params -> {
            throw exception;
        });

        assertThatThrownBy(() -> glmClient.chat("system", "user"))
                .isInstanceOf(UnexpectedStatusCodeException.class)
                .hasMessageContaining("400");
    }

    private ChatCompletion completion(String content) {
        return ChatCompletion.builder()
                .id("chatcmpl-test")
                .created(0L)
                .model("glm-test")
                .addChoice(ChatCompletion.Choice.builder()
                        .index(0)
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .logprobs(Optional.empty())
                        .message(ChatCompletionMessage.builder()
                                .content(content)
                                .refusal(Optional.empty())
                                .role(JsonValue.from("assistant"))
                                .build())
                        .build())
                .build();
    }

    private ChatCompletion emptyCompletion() {
        return ChatCompletion.builder()
                .id("chatcmpl-test")
                .created(0L)
                .model("glm-test")
                .choices(List.of())
                .build();
    }
}
