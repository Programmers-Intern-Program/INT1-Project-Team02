package com.flodiback.global.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.z.openapi.ZaiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;

/**
 * GLM(Z.AI) API 호출을 담당하는 공용 클라이언트입니다.
 *
 * <p>팀 내 어느 도메인에서든 GLM을 호출할 때 이 클래스를 주입받아 사용하세요.
 * 직접 ZaiClient를 사용하지 않도록 합니다.
 *
 * <h3>설정 (application.yml)</h3>
 * <pre>
 * glm.api.key=발급받은_API_KEY
 * glm.api.model=glm-5.1
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

    private final ZaiClient zaiClient;
    private final String model;

    /**
     * @param apiKey application.properties의 {@code glm.api.key} 값
     * @param model  application.properties의 {@code glm.api.model} 값 (예: glm-5.1)
     */
    public GlmClient(@Value("${glm.api.key}") String apiKey, @Value("${glm.api.model}") String model) {
        this.zaiClient = ZaiClient.builder().ofZAI().apiKey(apiKey).build();
        this.model = model;
    }

    /**
     * GLM에 system/user 메시지를 전송하고 응답 텍스트를 반환합니다.
     *
     * @param systemPrompt GLM의 역할과 응답 형식을 지정하는 시스템 메시지
     * @param userPrompt   실제 분석/처리할 내용을 담은 사용자 메시지
     * @return GLM이 생성한 응답 텍스트
     * @throws RuntimeException GLM API 호출 실패 시
     *
     * <p><b>주의:</b> JSON 응답이 필요한 경우 systemPrompt에 반드시 JSON 형식을 명시하세요.
     * GLM이 마크다운 코드블록(```json ... ```)으로 감싸서 응답할 수 있으므로,
     * 파싱 전에 코드블록 제거 처리를 권장합니다.
     */
    public String chat(String systemPrompt, String userPrompt) {
        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(List.of(
                        ChatMessage.builder()
                                .role(ChatMessageRole.SYSTEM.value())
                                .content(systemPrompt)
                                .build(),
                        ChatMessage.builder()
                                .role(ChatMessageRole.USER.value())
                                .content(userPrompt)
                                .build()))
                .stream(false)
                .build();

        return zaiClient
                .chat()
                .createChatCompletion(request)
                .getData()
                .getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .toString();
    }
}
