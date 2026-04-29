package com.flodiback.global.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GLM(Z.AI) API 호출을 담당하는 공용 클라이언트입니다.
 *
 * <p>팀 내 어느 도메인에서든 GLM을 호출할 때 이 클래스를 주입받아 사용하세요.
 *
 * <h3>설정 (application.yml)</h3>
 * <pre>
 * glm.api.key=발급받은_API_KEY
 * glm.api.model=glm-5.1
 * glm.api.url=https://api.z.ai/api  # 기본값, 변경 불필요
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

    private final RestClient restClient;
    private final String model;

    public GlmClient(
            @Value("${glm.api.key}") String apiKey,
            @Value("${glm.api.model}") String model,
            @Value("${glm.api.url}") String apiUrl) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ── Chat Completion ──────────────────────────────────────────────────────

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
        ChatRequest request = new ChatRequest(
                model, List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userPrompt)), false);

        ChatResponse response = restClient
                .post()
                .uri("/paas/v4/chat/completions")
                .body(request)
                .retrieve()
                .body(ChatResponse.class);

        return response.choices().get(0).message().content();
    }

    // ── Agent Chat ───────────────────────────────────────────────────────────

    /**
     * Agent Chat API를 호출하고 어시스턴트 응답 텍스트를 반환합니다.
     *
     * <p>Agent Chat은 Z.AI 플랫폼에 미리 구성된 에이전트를 호출합니다.
     * 에이전트 ID는 Z.AI 콘솔에서 확인할 수 있습니다.
     *
     * @param agentId     호출할 에이전트 ID (예: "general_translation")
     * @param userMessage 사용자 입력 메시지
     * @return 에이전트가 생성한 응답 텍스트
     */
    public String agentChat(String agentId, String userMessage) {
        AgentRequest request = new AgentRequest(
                agentId, List.of(new AgentMessage("user", List.of(new AgentContent("text", userMessage)))), false);

        AgentResponse response =
                restClient.post().uri("/v1/agents").body(request).retrieve().body(AgentResponse.class);

        List<AgentMessage> messages = response.choices().get(0).messages();
        return messages.get(messages.size() - 1).content().toString();
    }

    // ── Chat Completion 요청/응답 ─────────────────────────────────────────────

    private record ChatRequest(String model, List<ChatMessage> messages, boolean stream) {}

    private record ChatMessage(String role, String content) {}

    private record ChatResponse(List<ChatChoice> choices) {}

    private record ChatChoice(ChatMessage message) {}

    // ── Agent Chat 요청/응답 ──────────────────────────────────────────────────

    private record AgentRequest(
            @JsonProperty("agent_id") String agentId, List<AgentMessage> messages, boolean stream) {}

    private record AgentMessage(String role, Object content) {}

    private record AgentContent(String type, String text) {}

    private record AgentResponse(List<AgentChoice> choices) {}

    private record AgentChoice(List<AgentMessage> messages) {}
}
