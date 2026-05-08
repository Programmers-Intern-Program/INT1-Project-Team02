---
generated-by: ai-draft
reviewed-by: zunobk
reviewed-at: 2026-05-08
evidence: PR64
---

# GLM Timeout Retry Latency Logging

> 외부 GLM API 지연이나 일시 장애가 서버 자원을 오래 점유하지 않도록 timeout, retry, latency logging을 추가했다.

- 구현 일자: 2026-05-08
- 작성자: codex
- 해당 파트: GLM client, AI answer pipeline
- 관련 테이블: 없음

---

## 왜 이렇게 구현했나

### 문제 상황
GLM 호출은 외부 네트워크와 모델 응답 시간에 의존한다. timeout이 명확하지 않으면 GLM이 느릴 때 요청 스레드가 오래 대기하고, 장애 상황에서 병목 위치를 로그로 확인하기 어렵다.

### 선택한 방식
기존 OpenAI-compatible GLM Gateway 호출 구조는 유지하고, OpenAI Java SDK의 `timeout(Duration)`와 `maxRetries(int)` 설정을 사용했다. 호출 성공/실패 시 모델명, 소요 시간, 선택지 개수, 예외 유형만 로그로 남긴다.

### 고려했던 다른 방법들
| 방식 | 장점 | 단점 | 선택 여부 |
|------|------|------|-----------|
| SDK timeout/retry | 의존성 추가 없이 빠르게 적용 가능 | circuit breaker보다 장애 격리 기능은 제한적 | 선택 |
| Resilience4j circuit breaker | 장애 격리와 fallback 정책을 더 세밀하게 제어 가능 | MVP 범위 대비 설정과 테스트가 커짐 | 미선택 |

### 이 방식을 선택한 이유
현재 단계에서는 GLM 장애를 무한 대기하지 않게 막고, 병목 확인용 latency 로그를 확보하는 것이 핵심이다. 새 인프라나 의존성 없이 기존 클라이언트 계층만 보강하는 방식이 가장 작은 변경이다.

---

## 핵심 구현 내용

### 구조 설명
`GlmClient`가 OpenAI SDK 클라이언트를 만들 때 timeout과 retry 설정을 주입한다. `chat()`은 GLM 호출 시간을 측정하고, 성공/실패 로그를 남긴 뒤 기존처럼 응답 텍스트를 반환하거나 예외를 다시 던진다.

### 핵심 코드
- 설정: `glm.api.timeout-ms`, `glm.api.max-retries`
- 클라이언트 생성: `OpenAIOkHttpClient.builder().timeout(...).maxRetries(...)`
- 로그: prompt 원문, 응답 원문, API key는 남기지 않음

### 설계 결정사항
- 기본 timeout은 8000ms로 둔다.
- 기본 retry는 1회로 둔다.
- GLM 실패 시 상위 speech 흐름의 기존 fallback 정책을 유지한다.

---

## 다음 개선 과제

- [ ] 실제 운영 로그를 기반으로 GLM 평균/최대 latency를 측정한다.
- [ ] 장애가 반복될 경우 Resilience4j circuit breaker 도입을 검토한다.
- [ ] GLM timeout 발생률을 메트릭으로 집계한다.

---

## 포트폴리오 메모

> 외부 LLM API 지연으로 서버 요청 자원이 장시간 점유될 수 있는 문제를 발견하고, SDK timeout/retry와 latency logging을 적용해 장애 격리 기반을 마련했다. 민감한 prompt와 API key는 로그에서 제외해 운영 안전성을 함께 고려했다.
