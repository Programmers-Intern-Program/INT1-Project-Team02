# 2026-04-27-095128-package-architecture-codex

- 작성자: codex
- 상태: 완료(읽기 전용)
- 생성시각(로컬): 2026-04-27 09:51:28

## 배경
현재 패키지는 `domain/<도메인>/<세부도메인>/<layer>` 형태로 구성되어 있어 controller와 DTO가 도메인 패키지 아래에 있습니다.
ArchUnit은 `domain`이 global 응답 구현에 의존하지 않도록 검사하지만, controller가 `RsData`를 사용하면서 현재 테스트가 실패합니다.

## 목표
- API, application, domain, infrastructure, global 경계를 명확히 분리합니다.
- HTTP 응답 포맷과 Spring Web 의존성은 API 계층에 둡니다.
- 도메인 모델과 repository port는 domain 계층에 둡니다.
- JPA 구현체는 infrastructure 계층에 둡니다.
- ArchUnit 규칙을 새 구조에 맞게 갱신합니다.

## 범위
- meeting API/controller/DTO/service/repository 패키지 이동
- `MeetingStatus`를 meeting 도메인 타입으로 이동
- ArchUnit 구조 규칙 갱신
- 아키텍처 및 스키마 참조 문서 갱신

## 비범위
- 실제 회의 생성/종료 비즈니스 로직 구현
- DB 테이블/컬럼 변경
- 외부 Discord/embedding 연동 구현

## 단계
1. active 실행 계획과 폴더 기준을 추가합니다.
2. API DTO/controller를 `com.flodiback.api.meeting`으로 이동합니다.
3. meeting service를 `com.flodiback.application.meeting`으로 이동하고 command/result 모델을 둡니다.
4. meeting repository를 domain port와 infrastructure JPA adapter로 분리합니다.
5. `MeetingStatus`를 domain meeting type으로 이동합니다.
6. ArchUnit 규칙과 문서를 새 구조에 맞게 갱신합니다.
7. 포맷, 테스트, 빌드 검증 후 completed로 이동합니다.

## 위험 및 롤백
- 패키지 이동으로 import가 누락될 수 있습니다. `./gradlew test`와 `./gradlew build`로 확인합니다.
- repository port/adaptor 분리 과정에서 Spring bean wiring이 틀어질 수 있습니다. context load 테스트로 확인합니다.
- 롤백은 패키지 이동 커밋을 되돌리는 방식으로 수행합니다.

## 검증
- [x] 빌드: `./gradlew build`
- [x] 테스트: `./gradlew test`
- [x] 포맷 검사: `./gradlew spotlessCheck`
- [x] 계약 검사: `bash ./scripts/check-agent-docs.sh`

## 결정 로그
- 2026-04-27 09:51:28: controller/DTO는 api, service는 application, entity/repository port/type은 domain, JPA 구현은 infrastructure로 분리하기로 결정.
- 2026-04-27 09:59:00: 구현 기록 AI 초안은 현재 문서 검사에서 검토 필드 필수 조건에 걸리므로 생성하지 않고 실행 계획 완료 기록으로 설계 변경 이력을 남기기로 결정.
- 2026-04-27 10:00:00: 포맷, 테스트, 빌드, 문서 검사를 통과해 completed로 이동.
