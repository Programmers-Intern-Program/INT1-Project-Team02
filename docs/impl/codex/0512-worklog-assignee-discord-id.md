---
generated-by: ai-draft
reviewed-by:
reviewed-at:
evidence:
---

# WorkLog 담당자 Discord ID 저장

> 회의 종료 분석에서 GLM이 담당자 이름을 직접 확정하지 않고, 서버가 speaker key를 실제 Discord 사용자 ID로 매핑해 WorkLog에 저장하도록 개선했다.

- 구현 일자: 2026-05-12
- 작성자: codex
- 해당 파트: meeting analysis, meeting context, worklog
- 관련 테이블: work_logs, utterances

---

## 왜 이렇게 구현했나

### 문제 상황
기존 WorkLog는 `assigneeName`만 저장해 동명이인, 표시 이름 변경, "제가 할게요" 같은 발화자 지시를 안정적으로 추적하기 어려웠다.

### 선택한 방식
회의 전체 발화자 목록에서 `S1`, `S2` 같은 임시 speaker key를 만들고, prompt에는 이 key만 노출한다. GLM은 `assigneeSpeakerKey`만 반환하고, 서버가 key를 `speakerDiscordId`와 표시 이름으로 매핑해 WorkLog를 저장한다.

### 다른 방법
| 방식 | 장점 | 단점 | 선택 여부 |
|------|------|------|----------|
| prompt에 Discord ID 직접 노출 | 구현 단순 | 외부 모델에 raw 식별자 노출 | 미선택 |
| GLM이 assigneeName 반환 | 기존 구조 유지 | 이름/id 불일치와 추론 모호성 지속 | 미선택 |
| 서버 speaker key 매핑 | 식별자 노출 최소화, 서버가 최종 확정 | DTO/스키마 변경 필요 | 선택 |

---

## 핵심 구현 내용

### 구조 설명
`UtteranceRepository`가 회의 전체 distinct 발화자를 첫 발화 ID 기준으로 조회하고, `MeetingAnalysisService`가 이를 `S1`, `S2`로 매핑한다. WorkLog 저장 DTO와 entity에는 nullable `assigneeDiscordId`를 추가해 기존 API 호환성을 유지했다.

### 설계 결정사항
- speaker key 순서: `firstUtteranceId ASC` 기준으로 결정한다.
- unknown speaker: `speakerDiscordId`가 없거나 key 매핑이 실패하면 `담당자 미정`과 `assigneeDiscordId=null`로 저장한다.
- rolling summary: key가 없는 summary-only 담당자는 ID를 확정하지 않는다.

---

## 다음에 개선할 점

- [ ] WorkLog 담당자 변경/재배정 API가 생기면 `assigneeDiscordId` 갱신 정책도 함께 정의한다.
- [ ] rolling summary에 speaker key 메타데이터를 포함할지 별도 검토한다.

---

## 히스토리 메모

> WorkLog 담당자를 문자열로만 저장해 실제 사용자 식별이 어려운 문제를 발견했고, prompt용 speaker key를 서버 매핑 테이블로 되돌리는 방식으로 해결했다. 그 결과 GLM은 담당자 key만 선택하고, 서버가 최종 담당자 이름과 Discord ID를 확정한다.
