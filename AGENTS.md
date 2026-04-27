# AGENTS.md

## 미션
이 저장소는 Discord 기반 회의 도우미의 Spring Boot 백엔드 `flodi-back`를 담고 있습니다.
시스템은 회의 컨텍스트, 결정사항, 작업 로그, 봇 연동용 내부 API를 관리합니다.

## 처음 5분
1. 먼저 [docs/README.md](docs/README.md)에서 문서 지도를 확인합니다.
2. [ARCHITECTURE.md](ARCHITECTURE.md)에서 패키지 경계와 데이터 구조를 확인합니다.
3. `./gradlew test`를 실행합니다.
4. 작업이 작지 않다면 `docs/exec-plans/active/`에 실행 계획 문서를 먼저 만듭니다.

## 기본 명령어
- 빌드: `./gradlew build`
- 테스트: `./gradlew test`
- 포맷 검사: `./gradlew spotlessCheck`
- 포맷 자동 정리: `./gradlew spotlessApply`
- 에이전트 문서 일관성 검사: `./scripts/check-agent-docs.sh`
- 문서 가드닝 리포트 생성: `./scripts/doc-gardening.sh`

## 완료 기준
- 로컬에서 빌드와 테스트가 통과합니다.
- 포맷 검사가 통과합니다.
- 아키텍처, 계약, 워크플로에 영향이 있으면 문서를 함께 갱신합니다.
- 실행 계획이 있었다면 `active/`에서 `completed/`로 이동해 기록을 남깁니다.

## 아키텍처 지도
- 코어 패키지 루트: `src/main/java/com/flodiback`
- API 엔트리포인트: `api/*`
- 유스케이스 서비스: `application/*`
- 도메인 모델: `domain/*/entity`
- 기술 구현체: `infrastructure/*`
- 공통 관심사: `global/*`
- 엔트리포인트: `FlodiBackApplication`
- 컨텍스트/계약 문서:
  - [ARCHITECTURE.md](ARCHITECTURE.md)
  - [QUALITY_SCORE.md](QUALITY_SCORE.md)
  - [RELIABILITY.md](RELIABILITY.md)
  - [SECURITY.md](SECURITY.md)
  - [docs/references/internal-api-contracts.md](docs/references/internal-api-contracts.md)
  - [docs/generated/db-schema.md](docs/generated/db-schema.md)
  - [docs/exec-plans/tech-debt-tracker.md](docs/exec-plans/tech-debt-tracker.md)

## 작업 합의 (Harness Engineering)
- 이 파일은 길게 쓰지 말고, 문서 진입 지도 역할에 집중합니다.
- 오래 유지해야 하는 결정/명세는 `docs/` 하위 버전 관리 문서에 기록합니다.
- 큰 변경보다 짧은 주기의 점진적 변경을 우선합니다.
- 반복되는 리뷰 이슈는 문서나 자동 검사로 흡수합니다.
- 도메인 경계와 API 계약은 명시적으로 유지합니다.

## 변경 계획 규칙
- 작은 변경: 코드와 관련 문서를 같은 PR에서 같이 수정합니다.
- 수시간 이상 변경:
  - `./scripts/new-exec-plan.sh <topic> <owner>`로 `docs/exec-plans/active/YYYY-MM-DD-HHMMSS-topic-owner.md` 생성
  - 목표, 체크포인트, 결정 로그, 검증 결과 기록
  - 완료 후 문서 담당 1명이 `docs/exec-plans/completed/YYYY-MM/`로 이동

## 구현 기록 규칙
- 새 기능/주요 설계 변경은 `docs/impl/{author-kebab-case}/`에 구현 기록을 남깁니다.
- 파일명은 `{MMDD}-{feature-kebab-case}.md`를 사용합니다.
- 템플릿은 `docs/references/impl-record-template.md`를 따릅니다.
- 단순 오타, 포맷, 작은 테스트 수정은 생략할 수 있습니다.
- AI가 구현 기록 초안을 작성할 때는 `reviewed-by`, `reviewed-at`, `evidence`를 임의로 채우지 않습니다.
- 수치, 실험 결과, 대안 검토 내용은 확인된 근거가 있을 때만 작성합니다.
- AI가 생성한 구현 기록은 PR 병합 전 `reviewed-by`, `reviewed-at`, `evidence`를 채워 검토 완료 상태로 전환합니다.

## 계약 안정성 규칙
- 내부 API가 바뀌면 `docs/references/internal-api-contracts.md`를 반드시 갱신합니다.
- 데이터 모델이 바뀌면 `docs/generated/db-schema.md`를 반드시 갱신합니다.
- 하위 호환이 깨지는 변경은 실행 계획 문서에 마이그레이션 메모를 남깁니다.

## 가드닝 규칙
- 실행 계획의 active 상태가 길어지면 `doc-gardening` 리포트에서 점검합니다.
- 반복 TODO/FIXME는 실행 계획 또는 기술 부채 항목으로 승격합니다.
- `completed/` 파일은 읽기 전용으로 간주하고 신규 파일 추가만 허용합니다.

## 보안/신뢰성 체크포인트
- 시크릿, API 키, 민감한 원문 페이로드를 로그로 남기지 않습니다.
- 잘못된 입력은 공통 예외 처리 흐름으로 일관되게 처리합니다.
- 재현 가능한 검증 절차와 결정적 동작을 우선합니다.
