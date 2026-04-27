# 2026-04-27-112003-hybrid-package-architecture-codex

- 작성자: codex
- 상태: 완료(읽기 전용)
- 생성시각(로컬): 2026-04-27 11:20:03

## 배경
5계층 패키지 구조는 의존성 방향이 선명하지만, 팀이 처음 접하는 구조라 온보딩 비용이 큽니다.
팀 합의에 맞춰 controller만 `api`로 분리하고 나머지는 기존 도메인 기능 모듈 구조로 단순화합니다.

## 목표
- `@RestController`는 `api` 패키지에만 둡니다.
- DTO/service/repository/entity는 기존 도메인 기능 모듈 아래에 둡니다.
- domain 전체가 HTTP/API/응답 래퍼에 의존하지 않도록 ArchUnit으로 검증합니다.
- application/infrastructure 계층과 command/result 모델은 제거합니다.

## 범위
- meeting controller import 조정
- meeting DTO/service/repository/status 패키지 복원
- entity 패키지 경로 복원
- ArchUnit 규칙과 아키텍처 문서 갱신

## 비범위
- 회의 생성/종료 비즈니스 로직 구현
- DB 테이블/컬럼 변경
- API 계약 변경

## 단계
1. 도메인 기능 모듈 경로를 복원합니다.
2. application/infrastructure 패키지를 제거합니다.
3. ArchUnit 규칙을 하이브리드 구조 기준으로 단순화합니다.
4. 문서와 기술부채 트래커를 갱신합니다.
5. 포맷, 테스트, 빌드, 문서 검사를 수행합니다.

## 위험 및 롤백
- 파일 이동 과정에서 import가 누락될 수 있으므로 `./gradlew test`로 컴파일과 컨텍스트 로드를 확인합니다.
- ArchUnit 규칙이 DTO까지 포함한 domain 전체에 적용되므로, domain DTO에 Spring Web/RsData 의존을 추가하면 테스트가 실패합니다.
- 롤백은 이번 패키지 이동 변경을 되돌리는 방식으로 수행합니다.

## 검증
- [x] 빌드: `./gradlew build`
- [x] 테스트: `./gradlew test`
- [x] 포맷 검사: `./gradlew spotlessCheck`
- [x] 계약 검사: `bash ./scripts/check-agent-docs.sh`

## 결정 로그
- 2026-04-27 11:20:03: 팀 온보딩을 우선해 controller만 `api`에 두고, 나머지는 기존 도메인 기능 모듈 구조로 복원하기로 결정.
- 2026-04-27 11:24:00: domain 전체에 HTTP/API/RsData 의존 금지 규칙을 적용하고, service/repository/entity 개별 중복 규칙은 두지 않기로 결정.
- 2026-04-27 11:25:00: 포맷, 테스트, 빌드, 문서 검사를 통과해 completed로 이동.
