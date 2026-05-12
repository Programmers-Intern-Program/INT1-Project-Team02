package com.flodiback.global.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GlmClient {

    private final Function<ChatCompletionCreateParams, ChatCompletion> completionRequester;
    private final String model;
    private final long timeoutMs;
    private final int maxRetries;

    @Autowired
    public GlmClient(
            @Value("${glm.api.key}") String apiKey,
            @Value("${glm.api.model}") String model,
            @Value("${glm.api.url}") String apiUrl,
            @Value("${glm.api.timeout-ms:30000}") long timeoutMs,
            @Value("${glm.api.max-retries:1}") int maxRetries) {
        this(model, timeoutMs, maxRetries, createCompletionRequester(apiKey, apiUrl, timeoutMs, maxRetries));
    }

    GlmClient(String model, Function<ChatCompletionCreateParams, ChatCompletion> completionRequester) {
        this(model, -1L, -1, completionRequester);
    }

    GlmClient(
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
            String apiKey, String apiUrl, long timeoutMs, int maxRetries) {
        log.info(
                "GLM client initialized. url={}, keyLength={}, timeoutMs={}, maxRetries={}",
                apiUrl,
                apiKey.length(),
                timeoutMs,
                maxRetries);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(apiUrl)
                .timeout(Duration.ofMillis(timeoutMs))
                .maxRetries(maxRetries)
                .build();

        return params -> client.chat().completions().create(params);
    }

    public String chat(String systemPrompt, String userPrompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .model(model)
                .build();

        long startedAtNanos = System.nanoTime();
        try {
            ChatCompletion completion = completionRequester.apply(params);
            int choiceCount = completion.choices().size();

            if (choiceCount == 0) {
                throw new IllegalStateException("GLM response has no choices.");
            }

            ChatCompletion.Choice firstChoice = completion.choices().get(0);
            String content = firstChoice.message().content().orElse("");
            long latencyMs = elapsedMillis(startedAtNanos);
            log.info(
                    "GLM call succeeded. model={}, timeoutMs={}, maxRetries={}, latencyMs={}, choiceCount={}, finishReason={}, responseChars={}",
                    model,
                    timeoutMs,
                    maxRetries,
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
                        "GLM call failed. model={}, timeoutMs={}, maxRetries={}, latencyMs={}, exceptionType={}, message={}, rootCauseType={}, rootCauseMessage={}, statusCode={}, errorType={}, errorCode={}, errorParam={}, errorBodyPreview={}",
                        model,
                        timeoutMs,
                        maxRetries,
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
                        "GLM call failed. model={}, timeoutMs={}, maxRetries={}, latencyMs={}, exceptionType={}, message={}, rootCauseType={}, rootCauseMessage={}",
                        model,
                        timeoutMs,
                        maxRetries,
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
