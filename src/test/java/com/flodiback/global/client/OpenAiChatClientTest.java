package com.flodiback.global.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;

class OpenAiChatClientTest {

    @Test
    void chat_returnsFirstChoiceContent() {
        OpenAiChatClient openAiChatClient = new OpenAiChatClient("openai-test", params -> completion("answer"));

        String result = openAiChatClient.chat("system", "user");

        assertThat(result).isEqualTo("answer");
    }

    @Test
    void chat_setsMaxCompletionTokens_whenProvided() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        OpenAiChatClient openAiChatClient = new OpenAiChatClient("openai-test", params -> {
            capturedParams.set(params);
            return completion("answer");
        });

        String result = openAiChatClient.chat("system", "user", 256);

        assertThat(result).isEqualTo("answer");
        assertThat(capturedParams.get().maxCompletionTokens()).contains(256L);
    }

    @Test
    void chat_doesNotSetMaxCompletionTokens_whenNotProvided() {
        AtomicReference<ChatCompletionCreateParams> capturedParams = new AtomicReference<>();
        OpenAiChatClient openAiChatClient = new OpenAiChatClient("openai-test", params -> {
            capturedParams.set(params);
            return completion("answer");
        });

        String result = openAiChatClient.chat("system", "user");

        assertThat(result).isEqualTo("answer");
        assertThat(capturedParams.get().maxCompletionTokens()).isEmpty();
    }

    @Test
    void chat_throwsException_whenChoicesIsEmpty() {
        OpenAiChatClient openAiChatClient = new OpenAiChatClient("openai-test", params -> emptyCompletion());

        assertThatThrownBy(() -> openAiChatClient.chat("system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("choices");
    }

    @Test
    void chat_rethrowsException_whenOpenAiCallFails() {
        OpenAiChatClient openAiChatClient = new OpenAiChatClient("openai-test", params -> {
            throw new IllegalStateException("network error");
        });

        assertThatThrownBy(() -> openAiChatClient.chat("system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("network error");
    }

    @Test
    void chat_rethrowsIoException_whenOpenAiCallFails() {
        OpenAiChatClient openAiChatClient = new OpenAiChatClient("openai-test", 30000L, 0, params -> {
            throw new OpenAIIoException("Request failed", new java.net.SocketTimeoutException("Read timed out"));
        });

        assertThatThrownBy(() -> openAiChatClient.chat("system", "user"))
                .isInstanceOf(OpenAIIoException.class)
                .hasMessageContaining("Request failed");
    }

    @Test
    void chat_rethrowsServiceException_whenOpenAiReturnsErrorStatus() {
        ErrorObject error = ErrorObject.builder()
                .code("bad_request")
                .message("invalid request")
                .param(Optional.of("messages"))
                .type("invalid_request_error")
                .build();
        UnexpectedStatusCodeException exception = UnexpectedStatusCodeException.builder()
                .statusCode(400)
                .headers(Headers.builder().build())
                .error(error)
                .build();
        OpenAiChatClient openAiChatClient = new OpenAiChatClient("openai-test", 30000L, 0, params -> {
            throw exception;
        });

        assertThatThrownBy(() -> openAiChatClient.chat("system", "user"))
                .isInstanceOf(UnexpectedStatusCodeException.class)
                .hasMessageContaining("400");
    }

    private ChatCompletion completion(String content) {
        return ChatCompletion.builder()
                .id("chatcmpl-test")
                .created(0L)
                .model("openai-test")
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
                .model("openai-test")
                .choices(List.of())
                .build();
    }
}
