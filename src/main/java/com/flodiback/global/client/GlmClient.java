package com.flodiback.global.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import lombok.extern.slf4j.Slf4j;

/**
 * GLM(Z.AI) API 호출을 담당하는 공용 클라이언트입니다.
 *
 * <p>OpenAI-compatible 형태의 GLM Gateway를 OpenAI Java SDK로 호출합니다.
 * 팀 내 어느 도메인에서든 GLM을 호출할 때 이 클래스를 주입받아 사용합니다.
 *
 * <h3>설정 (application.yml)</h3>
 *
 * <pre>
 * glm.api.key=발급받은_API_KEY
 * glm.api.model=glm-5.1
 * glm.api.url=https://...  # GLM Gateway base URL
 * glm.api.timeout-ms=8000
 * glm.api.max-retries=1
 * </pre>
 *
 * <h3>사용 예시</h3>
 *
 * <pre>
 * {@code
 * @Service
 * @RequiredArgsConstructor
 * public class MyService {
 *
 *     private final GlmClient glmClient;
 *
 *     public String doSomething() {
 *         String systemPrompt = "당신은 회의 보조자입니다.";
 *         String userPrompt = "다음 회의 내용을 분석해줘.";
 *         return glmClient.chat(systemPrompt, userPrompt);
 *     }
 * }
 * }
 * </pre>
 */
@Slf4j
@Component
public class GlmClient {

    private final Function<ChatCompletionCreateParams, ChatCompletion> completionRequester;
    private final String model;

    @Autowired
    public GlmClient(
            @Value("${glm.api.key}") String apiKey,
            @Value("${glm.api.model}") String model,
            @Value("${glm.api.url}") String apiUrl,
            @Value("${glm.api.timeout-ms:8000}") long timeoutMs,
            @Value("${glm.api.max-retries:1}") int maxRetries) {
        this(model, createCompletionRequester(apiKey, apiUrl, timeoutMs, maxRetries));
    }

    GlmClient(String model, Function<ChatCompletionCreateParams, ChatCompletion> completionRequester) {
        this.model = model;
        this.completionRequester = completionRequester;
    }

    private static Function<ChatCompletionCreateParams, ChatCompletion> createCompletionRequester(
            String apiKey, String apiUrl, long timeoutMs, int maxRetries) {
        // timeout/retry를 명시해 외부 GLM 장애가 서버 자원을 오래 붙잡지 않도록 합니다.
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(apiUrl)
                .timeout(Duration.ofMillis(timeoutMs))
                .maxRetries(maxRetries)
                .build();

        return params -> client.chat().completions().create(params);
    }

    /**
     * system/user 메시지를 GLM에 전달하고 응답 텍스트를 반환합니다.
     *
     * @param systemPrompt GLM의 역할과 답변 규칙을 지정하는 시스템 메시지
     * @param userPrompt 실제 질문과 컨텍스트가 담긴 사용자 메시지
     * @return GLM이 생성한 답변 텍스트
     *
     * <p>JSON 응답이 필요한 경우 systemPrompt에 반드시 JSON 형식을 명시해야 합니다.
     * GLM이 마크다운 코드블록으로 감싸서 응답할 수 있으므로,
     * 파싱 전에 코드블록 제거 처리를 권장합니다.
     */
    public String chat(String systemPrompt, String userPrompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .model(model)
                .build();

        // 호출 시간을 측정해 GLM 지연이 병목인지 운영 로그에서 확인할 수 있게 합니다.
        long startedAtNanos = System.nanoTime();
        try {
            ChatCompletion completion = completionRequester.apply(params);
            int choiceCount = completion.choices().size();

            if (choiceCount == 0) {
                throw new IllegalStateException("GLM 응답에 choices가 없습니다.");
            }

            long latencyMs = elapsedMillis(startedAtNanos);
            log.info("GLM 호출 성공. model={}, latencyMs={}, choiceCount={}", model, latencyMs, choiceCount);

            return completion.choices().get(0).message().content().orElse("");
        } catch (RuntimeException e) {
            long latencyMs = elapsedMillis(startedAtNanos);

            // 프롬프트와 응답 원문은 민감할 수 있으므로 실패 로그에도 남기지 않습니다.
            log.warn(
                    "GLM 호출 실패. model={}, latencyMs={}, exceptionType={}, message={}",
                    model,
                    latencyMs,
                    e.getClass().getSimpleName(),
                    e.getMessage());
            throw e;
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
