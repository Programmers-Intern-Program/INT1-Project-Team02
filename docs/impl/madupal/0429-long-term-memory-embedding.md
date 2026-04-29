---
generated-by: ai-draft
reviewed-by:
reviewed-at:
evidence:
---

# 장기 기억 시스템 - 임베딩 기반 결정사항 저장 및 하이브리드 검색

> 결정사항을 OpenAI 임베딩으로 벡터화해 저장하고, 질문 기반 하이브리드 검색(코사인 유사도 + 키워드)과 충돌 감지를 구현해 AI 에이전트의 장기 기억 품질을 개선했다.

- 구현 일자: 2026-04-29
- 작성자: madupal
- 담당 파트: D파트 (장기 기억 시스템)
- 관련 테이블: `decisions`, `decision_conflicts`

AI가 초안을 작성할 때는 `reviewed-by`, `reviewed-at`, `evidence`를 비워둡니다.
개발자가 사실관계와 근거를 검토한 뒤 직접 채웁니다.

---

## 왜 이렇게 구현했나

### 문제 상황

기존 컨텍스트 조립(`ContextService.assemble`) 방식은 프로젝트에 속한 결정사항 전체를 프롬프트에 주입했다. 결정사항이 누적될수록 토큰 비용이 선형으로 증가하며, 현재 질문과 관련 없는 결정사항까지 모두 포함되는 구조였다. 또한 유사한 내용의 결정사항이 중복으로 저장돼도 감지 수단이 없었다.

### 선택한 방식

- **임베딩 모델**: OpenAI `text-embedding-3-small` (1536차원, float[])
- **벡터 저장**: pgvector `VECTOR(1536)`, HNSW 인덱스 (`m=16, ef_construction=64`)
- **하이브리드 검색**: 코사인 유사도 × 0.7 + `ts_rank` 키워드 점수 × 0.3, top-5
- **충돌 감지**: 유사도 ≥ 0.92 → DIRECT_CONFLICT, ≥ 0.85 → POSSIBLE_UPDATE
- **소프트 삭제**: `is_active` 컬럼으로 관리, `decision_conflicts` FK 제약 위반 방지

### 고려했던 다른 방법들

| 방식 | 장점 | 단점 | 출처/근거 | 선택 여부 |
|------|------|------|-----------|----------|
| OpenAI text-embedding-3-small (1536d) | 공식 지원, RestClient로 간단히 통합, 팀 기존 OPENAI_API_KEY 재사용 | API 비용 발생, 네트워크 의존 | OpenAI 공식 문서 | 선택 |
| GLM 임베딩 (로컬, 768d) | 비용 없음 | 별도 추론 서버 필요, 운영 복잡도 증가, 768d → 1536d 마이그레이션 필요 | 팀 사전 설계 검토 | 미선택 |
| IVFFlat 인덱스 | 메모리 효율 | 사전 학습(nlist) 필요, 소규모 데이터에서 오버헤드 | pgvector 공식 문서 | 미선택 |
| HNSW 인덱스 | 학습 불필요, 소규모부터 즉시 사용 가능, 높은 recall | 메모리 사용량 다소 큼 | pgvector 공식 문서 | 선택 |
| 전체 결정사항 주입 (기존) | 구현 단순 | 토큰 비용 선형 증가 | 기존 코드 | 미선택 |

### 이 방식을 선택한 이유

HNSW는 IVFFlat와 달리 사전 학습 단계가 없어 초기 데이터가 적은 상황에서도 즉시 동작한다. OpenAI API는 팀 인프라에 이미 통합돼 있어 추가 운영 비용이 없다. 하이브리드 검색은 의미적 유사성(semantic)만 쓸 경우 키워드 일치를 놓치는 경우를 보완하기 위해 도입했다.

---

## 핵심 구현 내용

### 구조 설명

```
OpenAiEmbeddingClient          - RestClient로 OpenAI /v1/embeddings 호출
DecisionEmbeddingService       - 결정사항 저장 후 임베딩 처리 오케스트레이션
ConflictDetectionService       - 유사 결정사항 조회 후 DecisionConflict 기록
DecisionConflict (entity)      - decision_conflicts 테이블 매핑
DecisionRepository             - updateEmbedding, hybridSearch, findSimilarDecisionsRaw 네이티브 쿼리
ContextService.resolveDecisions - 질문 유무에 따라 hybridSearch 또는 전체 조회
```

### 핵심 코드

**임베딩 저장 (네이티브 쿼리로 분리)**

임베딩 컬럼은 `insertable=false, updatable=false`로 선언해 JPA가 관리하지 않고, 별도 네이티브 쿼리로만 갱신한다.

```java
// DecisionRepository
@Modifying
@Query(value = "UPDATE decisions SET embedding = CAST(:embedding AS vector) WHERE id = :id",
       nativeQuery = true)
void updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);
```

**하이브리드 검색 CTE**

```sql
WITH semantic AS (
    SELECT id, 1 - (embedding <=> CAST(:embedding AS vector)) AS semantic_score
    FROM decisions WHERE project_id = :projectId AND is_active = true AND embedding IS NOT NULL
),
combined AS (
    SELECT s.id,
           s.semantic_score * :semanticWeight
           + COALESCE(ts_rank(d.content_tsv, plainto_tsquery('simple', :queryText)), 0) * :keywordWeight
           AS total_score
    FROM semantic s JOIN decisions d ON s.id = d.id
)
SELECT d.* FROM decisions d JOIN combined c ON d.id = c.id
ORDER BY c.total_score DESC LIMIT :topK
```

**충돌 감지**

```java
// ConflictDetectionService
List<Object[]> rows = decisionRepository.findSimilarDecisionsRaw(
    newDecision.getProject().getId(), embeddingStr, newDecision.getId(), POSSIBLE_UPDATE_THRESHOLD);

ConflictType type = similarity >= DIRECT_CONFLICT_THRESHOLD
    ? ConflictType.DIRECT_CONFLICT
    : ConflictType.POSSIBLE_UPDATE;
```

### 설계 결정사항

- **임베딩 실패 시 결정사항 저장은 유지**: `DecisionEmbeddingService`에서 try-catch로 감싸 API 장애가 결정사항 저장을 막지 않는다.
- **content_tsv는 엔티티에서 제외**: DB 트리거(`trg_decisions_tsv`)가 자동 갱신하므로 Hibernate가 이 컬럼을 인식하면 `TSVECTOR` 타입 매핑 오류가 발생한다.
- **소프트 삭제**: `decision_conflicts`의 FK에 `ON DELETE CASCADE`가 없어 하드 삭제 시 FK 위반이 발생할 수 있다. `is_active = false`로 비활성화해 이를 방지한다.
- **ddl-auto: update**: Flyway와 병행하는 개발 단계에서 `validate`는 컬럼 타입 불일치로 실패 가능성이 있어 `update`를 유지한다.
- **Flyway V1/V2 분리**: 기존 DB에서 `baseline-version=1`로 V1을 건너뛰고 V2만 실행해 새 컬럼/인덱스를 적용한다. 신규 DB는 V1 → V2 순서로 실행된다.
- **테스트에서 Flyway 비활성화**: TestContainers + `ddl-auto=create-drop`으로 스키마를 관리하고 Flyway는 끈다. pgvector 익스텐션은 `withInitScript("init-pgvector.sql")`로 컨테이너 시작 시 로드한다.

---

## 어려웠던 점 & 해결 방법

### 문제 1: 신규 DB에서 V2 DO 블록이 embedding 컬럼을 중복 추가하려는 시도

- 상황: V1에서 `CREATE TABLE decisions (..., embedding VECTOR(1536))` 직후 V2 DO 블록이 실행되면, 컬럼이 이미 존재하지만 `vector(1536)` 타입 체크를 하지 않아 불필요한 DROP+ADD가 시도될 수 있었다.
- 시도한 것: `IF NOT EXISTS (SELECT 1 FROM information_schema.columns ...)` 조건 추가.
- 해결: `pg_catalog.format_type(atttypid, atttypmod)`로 현재 타입을 확인해 이미 `vector(1536)`이면 즉시 RETURN. 기존 데이터가 있는 상태에서 DROP+ADD를 시도하면 EXCEPTION으로 차단.

### 문제 2: PGvector JPA 타입 등록 복잡성

- 상황: Spring Data JPA에서 `PGvector` 타입을 매개변수로 받는 네이티브 쿼리를 실행하면 Hibernate 타입 변환 오류가 발생했다.
- 시도한 것: `CAST(:embedding AS vector)` 패턴 사용.
- 해결: `PGvector` 객체 대신 `.getValue()`로 추출한 문자열(`[0.1,0.2,...]`)을 바인딩하고, SQL에서 `CAST(... AS vector)`로 변환. 별도 Hibernate 커스텀 타입 등록 불필요.

### 문제 3: TSVECTOR 컬럼 Hibernate 매핑 오류

- 상황: `content_tsv` 컬럼을 `String`으로 매핑하면 Hibernate의 타입 불일치로 `ddl-auto: validate` 실패.
- 해결: 엔티티에서 `content_tsv` 필드를 완전히 제거. DB 트리거가 자동으로 갱신하며 Java 코드는 이 컬럼을 읽거나 쓰지 않는다.

---

## 다음에 개선할 점

- [ ] `decision_conflicts` 감지 시 Discord 채널로 알림 전송 (현재는 DB 기록만)
- [ ] `is_active = false` 결정사항의 이력 조회 API 추가
- [ ] `ConflictResolution` 상태 변경 API 구현 (PENDING → UPDATED/KEPT)
- [ ] Flyway SQL 자체를 TestContainers로 검증하는 마이그레이션 통합 테스트 추가

---

## 포트폴리오 메모

> 결정사항이 누적될수록 전체 주입 방식의 토큰 비용이 선형으로 증가하는 문제를 발견했다.
> pgvector HNSW 인덱스와 OpenAI 임베딩을 결합한 하이브리드 검색(코사인 유사도 0.7 + 키워드 0.3)을 도입해 질문과 관련 있는 결정사항만 선택적으로 주입하도록 설계했고, 유사 결정사항 충돌 감지 기능도 함께 구현했다.
> 검토 전 문구이므로 포트폴리오에 사용하기 전에 실제 토큰 절감 수치와 검색 관련성 근거를 확인해 수정하세요.
