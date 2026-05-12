package com.flodiback.global.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.flodiback.global.config.AiProviderCondition.OpenAiProviderCondition;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Conditional(OpenAiProviderCondition.class)
public class OpenAiChatClient {

    private final Function<ChatCompletionCreateParams, ChatCompletion> completionRequester;
    private final String model;
    private final long timeoutMs;
    private final int maxRetries;

    @Autowired
    public OpenAiChatClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.chat.model}") String model,
            @Value("${openai.chat.base-url}") String baseUrl,
            @Value("${openai.chat.timeout-ms:30000}") long timeoutMs,
            @Value("${openai.chat.max-retries:0}") int maxRetries) {
        this(model, timeoutMs, maxRetries, createCompletionRequester(apiKey, baseUrl, timeoutMs, maxRetries));
    }

    OpenAiChatClient(String model, Function<ChatCompletionCreateParams, ChatCompletion> completionRequester) {
        this(model, -1L, -1, completionRequester);
    }

    OpenAiChatClient(
            String model,
            long timeoutMs,
            int maxRetries,
            Function<ChatCompletionCreateParams, ChatCompletion> completionRequester) {
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.completionRequester = completionRequester;
    }

    private static Function<ChatCompletionCreateParams, ChatCompletion> createCompletionRequester(
            String apiKey, String baseUrl, long timeoutMs, int maxRetries) {
        log.info(
                "OpenAI chat client initialized. url={}, keyLength={}, timeoutMs={}, maxRetries={}",
                baseUrl,
                apiKey.length(),
                timeoutMs,
                maxRetries);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(Duration.ofMillis(timeoutMs))
                .maxRetries(maxRetries)
                .build();

        return params -> client.chat().completions().create(params);
    }

    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, null);
    }

    public String chat(String systemPrompt, String userPrompt, int maxCompletionTokens) {
        return chat(systemPrompt, userPrompt, Long.valueOf(maxCompletionTokens));
    }

    private String chat(String systemPrompt, String userPrompt, Long maxCompletionTokens) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .model(model);
        if (maxCompletionTokens != null) {
            builder.maxCompletionTokens(maxCompletionTokens);
        }
        ChatCompletionCreateParams params = builder.build();

        long startedAtNanos = System.nanoTime();
        try {
            ChatCompletion completion = completionRequester.apply(params);
            int choiceCount = completion.choices().size();

            if (choiceCount == 0) {
                throw new IllegalStateException("OpenAI response has no choices.");
            }

            ChatCompletion.Choice firstChoice = completion.choices().get(0);
            String content = firstChoice.message().content().orElse("");
            long latencyMs = elapsedMillis(startedAtNanos);
            log.info(
                    "OpenAI chat call succeeded. model={}, timeoutMs={}, maxRetries={}, maxCompletionTokens={}, latencyMs={}, choiceCount={}, finishReason={}, responseChars={}",
                    model,
                    timeoutMs,
                    maxRetries,
                    maxCompletionTokens,
                    latencyMs,
                    choiceCount,
                    firstChoice.finishReason(),
                    content.length());

            return content;
        } catch (RuntimeException e) {
            long latencyMs = elapsedMillis(startedAtNanos);
            Throwable rootCause = rootCause(e);

            if (e instanceof OpenAIServiceException serviceException) {
                log.warn(
                        "OpenAI chat call failed. model={}, timeoutMs={}, maxRetries={}, maxCompletionTokens={}, latencyMs={}, exceptionType={}, message={}, rootCauseType={}, rootCauseMessage={}, statusCode={}, errorType={}, errorCode={}, errorParam={}, errorBodyPreview={}",
                        model,
                        timeoutMs,
                        maxRetries,
                        maxCompletionTokens,
                        latencyMs,
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        rootCause.getClass().getSimpleName(),
                        rootCause.getMessage(),
                        serviceException.statusCode(),
                        serviceException.type().orElse(null),
                        serviceException.code().orElse(null),
                        serviceException.param().orElse(null),
                        preview(serviceException.body().toString(), 500));
            } else {
                log.warn(
                        "OpenAI chat call failed. model={}, timeoutMs={}, maxRetries={}, maxCompletionTokens={}, latencyMs={}, exceptionType={}, message={}, rootCauseType={}, rootCauseMessage={}",
                        model,
                        timeoutMs,
                        maxRetries,
                        maxCompletionTokens,
                        latencyMs,
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        rootCause.getClass().getSimpleName(),
                        rootCause.getMessage());
            }
            throw e;
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String preview(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
