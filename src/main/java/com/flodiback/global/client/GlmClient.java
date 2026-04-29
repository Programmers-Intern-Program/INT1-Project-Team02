package com.flodiback.global.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

/**
 * GLM(Z.AI) API 호출을 담당하는 공용 클라이언트입니다.
 *
 * <p>팀 내 어느 도메인에서든 GLM을 호출할 때 이 클래스를 주입받아 사용하세요.
 *
 * <h3>설정 (application.yml)</h3>
 * <pre>
 * glm.api.key=발급받은_API_KEY
 * glm.api.model=glm-5.1
 * glm.api.url=https://...  # grepp 게이트웨이 base URL
 * </pre>
 *
 * <h3>사용 예시</h3>
 * <pre>
 * {@code
 * @Service
 * @RequiredArgsConstructor
 * public class MyService {
 *
 *     private final GlmClient glmClient;
 *
 *     public String doSomething() {
 *         String systemPrompt = "당신은 ...";
 *         String userPrompt   = "다음 내용을 분석해줘: ...";
 *         return glmClient.chat(systemPrompt, userPrompt);
 *     }
 * }
 * }
 * </pre>
 */
@Component
public class GlmClient {

    private final OpenAIClient client;
    private final String model;

    public GlmClient(
            @Value("${glm.api.key}") String apiKey,
            @Value("${glm.api.model}") String model,
            @Value("${glm.api.url}") String apiUrl) {
        this.model = model;
        this.client =
                OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(apiUrl).build();
    }

    /**
     * GLM에 system/user 메시지를 전송하고 응답 텍스트를 반환합니다.
     *
     * @param systemPrompt GLM의 역할과 응답 형식을 지정하는 시스템 메시지
     * @param userPrompt   실제 분석/처리할 내용을 담은 사용자 메시지
     * @return GLM이 생성한 응답 텍스트
     *
     * <p><b>주의:</b> JSON 응답이 필요한 경우 systemPrompt에 반드시 JSON 형식을 명시하세요.
     * GLM이 마크다운 코드블록(```json ... ```)으로 감싸서 응답할 수 있으므로,
     * 파싱 전에 코드블록 제거 처리를 권장합니다.
     */
    public String chat(String systemPrompt, String userPrompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .model(model)
                .build();

        ChatCompletion completion = client.chat().completions().create(params);

        return completion.choices().get(0).message().content().orElse("");
    }
}
