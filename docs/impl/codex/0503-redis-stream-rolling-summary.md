---
generated-by: ai-draft
reviewed-by: madupal
reviewed-at: 2026-05-04
evidence: PR 27
---

# Redis Streams Rolling Summary

> 회의 발화 저장 이후 Redis Stream wake-up 이벤트로 rolling summary 갱신을 비동기화했다.

- 구현 일자: 2026-05-03
- 작성자: codex
- 해당 파트: meeting context, speech ingest, Redis Streams
- 관련 테이블: `utterances`, `context_cache`

---

## 왜 이렇게 구현했나

### 문제 상황
회의 중 발화가 계속 쌓이면 질문 답변 프롬프트가 최근 원문 tail만으로는 장기 흐름을 잃고, 전체 발화를 매번 넣으면 토큰 사용량이 통제되지 않는다.

### 선택한 방식
발화 저장 트랜잭션이 커밋된 뒤 Redis Stream에 wake-up 이벤트를 발행하고, 단일 consumer가 DB 기준 미압축 발화 token 합계가 3000 이상인지 확인한다. 조건을 만족하면 최근 20턴은 원문 tail로 남기고 그 이전 발화만 GLM으로 압축해 `context_cache`에 새 버전으로 저장한다.

### 고려했던 다른 방법들
| 방식 | 장점 | 단점 | 선택 여부 |
|------|------|------|-----------|
| `@Async` + meeting lock | 구현이 단순함 | 단일 서버 메모리 락에 의존하고 재시작/확장 시 약함 | 미선택 |
| Redis Streams | Redis 도입 계획과 맞고 consumer group 기반 재처리 여지가 있음 | stream/group 초기화와 consumer 운영 코드가 추가됨 | 선택 |
| Redis Pub/Sub | WebSocket 브로드캐스트와 설정을 공유하기 쉬움 | 메시지 유실 시 복구가 어려움 | 미선택 |

### 이 방식을 선택한 이유
Redis가 WebSocket fan-out에도 사용될 예정이라 인프라 비용이 중복되지 않고, rolling summary 이벤트는 원문 데이터가 아니라 압축 필요 여부를 깨우는 신호라 Redis Streams의 consumer group 모델과 잘 맞는다.

---

## 핵심 구현 내용

### 구조 설명
`InternalSpeechService`는 발화 저장 시 근사 token count를 채우고 `UtteranceSavedEvent`를 발행한다. `RollingSummaryEventPublisher`는 커밋 이후 Redis Stream에 이벤트를 쓰며, `RollingSummaryConsumer`는 meeting별 누적 counter가 3000 이상일 때 `RollingSummaryService.compressIfNeeded()`를 호출한다.

### 핵심 코드
- Redis 설정: `RedisConfig`
- Stream 초기화: `RollingSummaryStreamInitializer`
- Stream 발행: `RollingSummaryEventPublisher`
- Stream 소비: `RollingSummaryConsumer`
- 압축 writer: `RollingSummaryService`
- Q&A 조립: `ContextService`가 `ContextCache` rolling summary와 cache 이후 최신 발화 20개를 반환

### 설계 결정사항
- Stream 메시지는 source of truth가 아니라 wake-up signal이다.
- token count가 null이면 0으로 합산한다.
- consumer 예외는 warn 로그 후 XACK한다. 다음 발화 이벤트에서 DB 기준으로 다시 압축 여부를 확인한다.
- cache가 없으면 기존처럼 최신 발화 20개만 사용하고 rolling summary는 null로 둔다.
- Stream listener는 `rollingSummaryStreamExecutor`를 사용해 polling 작업과 GLM summary 작업을 기본 스레드에서 분리한다.
- pending recovery는 현재 pending 개수 로깅까지만 수행한다. 실제 claim/reprocess 정책은 운영 안정화 단계에서 다룬다.
- Rolling summary watermark는 sequence 단독 기준에서 `utterances.created_at` grace window 기준으로 확장한다.
- 기존 cache row처럼 `compressed_until_created_at`이 비어 있으면 `end_sequence_no` fallback을 사용한다.

---

## 다음 개선 과제

- [ ] 여러 consumer를 운용할 때 meetingId partitioning 또는 Redis lock 정책을 추가한다.
- [ ] 정확한 tokenizer 기반 token count로 교체한다.
- [ ] pending message claim/replay 정책을 운영 요구에 맞게 강화한다.
- [ ] MeetingStartContextProvider가 rolling summary와 장기 memory를 함께 사용하도록 연결한다.

---

## 스토리 메모

> 회의 중 질문 응답이 모든 원문 발화에 끌려다니는 문제를 발견했고, 발화 저장과 summary 갱신을 Redis Stream wake-up 이벤트로 분리했다. 그 결과 발화 저장과 Q&A 흐름은 유지하면서, DB 기준 미압축 token 합계가 임계치를 넘을 때만 rolling summary가 새 버전으로 저장된다.
