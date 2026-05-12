---
generated-by: ai-draft
reviewed-by: madupal
reviewed-at: 2026-05-11
evidence: PR 94
---

# OpenAI AI Chat Provider

> 회의 중 AI 질문 경로에서 GLM과 OpenAI를 같은 `AiChatService` 인터페이스로 교체해 테스트할 수 있게 했다.

## 배경

GLM 수동 테스트와 실제 회의 질문 로그에서 짧은 프롬프트도 8~30초가 걸리는 현상이 확인됐다. DB/RAG보다 GLM 게이트웨이 또는 모델 호출 자체의 기본 지연이 커 보이므로, 같은 회의 질문 경로를 OpenAI 모델로 전환해 지연을 비교할 수 있는 설정이 필요했다.

## 구현

- `OpenAiChatClient`를 추가해 OpenAI Chat Completions API를 호출한다.
- `OpenAiChatService`를 추가해 기존 `AiChatService` 기반 호출부를 그대로 재사용한다.
- `AI_PROVIDER=openai|glm|disabled` 설정을 추가했다.
- 기존 `GLM_ENABLED=true` 설정도 `AI_PROVIDER`가 비어 있을 때 계속 GLM 선택으로 동작하게 유지했다.
- OpenAI 호출 성공/실패 로그는 GLM 로그와 같은 수준의 메타데이터만 남기고, 프롬프트/응답 본문은 남기지 않는다.
- `OpenAiChatClientManualTest`를 추가해 로컬에서 GLM 수동 테스트와 같은 방식으로 응답 시간을 비교할 수 있게 했다.

## 사용법

OpenAI로 회의 중 AI 답변을 테스트하려면 `.env`에 다음 값을 둔다.

```properties
AI_PROVIDER=openai
OPENAI_API_KEY=...
OPENAI_CHAT_MODEL=gpt-4o-mini
OPENAI_CHAT_TIMEOUT_MS=30000
OPENAI_CHAT_MAX_RETRIES=0
```

GLM으로 되돌리려면 `AI_PROVIDER=glm` 또는 기존처럼 `GLM_ENABLED=true`를 사용한다.

수동 클라이언트 테스트만 실행하려면 `OpenAiChatClientManualTest`의 `@Disabled`를 잠시 제거한 뒤 다음 명령을 실행한다.

```bash
./gradlew test --tests "com.flodiback.global.client.OpenAiChatClientManualTest"
```

## 검증

- `./gradlew test --tests "com.flodiback.global.client.OpenAiChatClientTest" --tests "com.flodiback.domain.ai.service.OpenAiChatServiceTest" --tests "com.flodiback.domain.ai.service.GlmAiChatServiceTest" --tests "com.flodiback.domain.ai.service.DisabledAiChatServiceTest"`

## 리뷰 메타

- reviewed-by:
- reviewed-at:
- evidence:
