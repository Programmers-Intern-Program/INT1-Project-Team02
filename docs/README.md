# 문서 지도

이 저장소는 하네스 엔지니어링 방식의 문서 구조를 사용합니다.

## 구조
- `design-docs/`: 오래 유지할 설계 원칙과 아키텍처 신념
- `exec-plans/`: 중간 이상 작업의 진행/완료 실행 계획
- `generated/`: 스키마 스냅샷 등 동기화되는 기술 참조 문서
- `impl/`: 작성자별 기능 구현 기록과 기술 선택/대안/문제 해결 기록
- `product-specs/`: 기능 동작과 사용자 관점 요구사항
- `references/`: API 계약, 테스트 명령어, 운영 참고사항
  - 예: `docs/generated/doc-gardening-report.md` (주간 자동 생성)

## 루트 운영 문서
- `QUALITY_SCORE.md`: 품질 점수판과 개선 목표
- `RELIABILITY.md`: 신뢰성 기준과 운영 체크리스트
- `SECURITY.md`: 보안 최소선과 점검 항목

## 에이전트 권장 읽기 순서
1. `AGENTS.md`
2. `ARCHITECTURE.md`
3. `QUALITY_SCORE.md` / `RELIABILITY.md` / `SECURITY.md`
4. `docs/references/internal-api-contracts.md`
5. `docs/generated/db-schema.md`
6. `docs/exec-plans/tech-debt-tracker.md`
7. `docs/impl/README.md`

## 유지 규칙
- 문서는 작게 유지하고, 서로 링크로 연결합니다.
- 채팅에만 남아 있는 결정은 가능한 한 `docs/`로 승격합니다.
- 동작이 바뀌면 같은 PR에서 관련 문서를 같이 수정합니다.
