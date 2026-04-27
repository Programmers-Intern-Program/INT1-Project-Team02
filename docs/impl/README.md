# 구현 기록

기능 구현 후 기술 선택, 대안, 문제 해결, 포트폴리오 메모를 작성자별로 기록합니다.
Claude, GPT, Codex 등 모든 AI 도구는 이 문서를 공통 규칙으로 사용합니다.

## 경로 규칙

- 구현 기록은 작성자별 폴더에 저장합니다.
- 작성자 폴더명은 GitHub ID 또는 팀에서 합의한 핸들을 사용합니다.
- 작성자 폴더명과 파일명은 소문자 kebab-case를 사용합니다.
- 경로: `docs/impl/{author-kebab-case}/{MMDD}-{feature-kebab-case}.md`
- 예: `docs/impl/madupal/0427-rag-search-service.md`

## 작성 대상

- 새 기능 구현
- 아키텍처, DB, API 계약에 영향이 있는 변경
- 기술 선택 근거가 필요한 구현
- 실험, 비교, 트러블슈팅이 있었던 구현
- 포트폴리오에 쓸 만한 문제 해결 사례

## 생략 가능

- 오타 수정
- 단순 포맷팅
- 테스트 이름 변경
- 별도 설계 결정이 없는 작은 버그 수정

## 충돌 방지 규칙

- 각 작성자는 자기 폴더의 구현 기록을 우선 수정합니다.
- 다른 작성자의 기록 수정은 PR 리뷰 코멘트로 먼저 제안합니다.
- 공통 템플릿 변경은 `docs/references/impl-record-template.md`에서만 합니다.

## 작성 방법

구현이 끝나면 `docs/references/impl-record-template.md`를 복사해 실제 내용으로 채웁니다.

## 권장 워크플로우

1. AI에게 구현 기록 초안 생성을 요청합니다.
2. 생성된 문서의 frontmatter에서 `reviewed-by`, `reviewed-at`, `evidence`를 개발자가 직접 채웁니다.
3. 수치, 대안 비교, 실험 결과가 실제 근거에 기반하는지 확인합니다.
4. 근거 없는 수치나 실제로 검토하지 않은 대안은 삭제합니다.
5. 포트폴리오 메모는 개발자가 사실관계와 표현을 직접 확인하고 수정합니다.

AI가 작성한 초안은 검토 전까지 확정 기록으로 간주하지 않습니다.
AI가 생성한 구현 기록은 PR 병합 전 `reviewed-by`, `reviewed-at`, `evidence`를 채워 검토 완료 상태로 전환합니다.

`evidence`에는 다음과 같은 확인 가능한 근거를 적을 수 있습니다.

- PR 번호
- 이슈 번호
- 테스트 명령과 결과
- 실험 로그
- 회의/팀 논의 날짜
- 공식 문서 링크
- 관련 코드 경로

예시 요청:

```text
방금 구현한 거 impl-record 템플릿대로 docs/impl/madupal/에 AI 초안으로 기록 만들어줘.
reviewed-by, reviewed-at, evidence는 비워둬.
```

또는 구현 요청과 함께 사용할 수 있습니다.

```text
decisions RAG 검색 서비스 구현하고, 끝나면 impl-record 템플릿대로 AI 초안 기록도 만들어줘.
reviewed-by, reviewed-at, evidence는 비워둬.
```
