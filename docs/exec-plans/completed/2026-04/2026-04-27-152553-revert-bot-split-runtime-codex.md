# 2026-04-27-152553-revert-bot-split-runtime-codex

- 작성자: codex
- 상태: 완료(읽기 전용)
- 생성시각(로컬): 2026-04-27 15:25:53

## 배경
- Discord 봇을 Spring 단일 진입점으로 통합했지만, 사용자 요구사항은 분리 실행(별도 main) 형태다.
- 봇 실험/장애 격리를 위해 `DiscordBotMain` 기반 실행 경로를 다시 복구해야 한다.

## 목표
- `DiscordBotMain` 분리 실행 구조를 복구한다.
- Spring 앱과 봇 실행 엔트리포인트를 다시 분리한다.
- 실행 가이드를 `runDiscordBot` 기준으로 되돌린다.

## 범위
- `DiscordBotLifecycle`/`DiscordBotProperties` 제거
- `DiscordBotMain` 복구
- Gradle 실행 태스크 복구(`runDiscordBot`, `runBot`)
- 관련 문서/설정 정리

## 비범위
- STT 연동
- Internal API 연동
- 봇 명령 확장

## 단계
1. 통합용 코드와 설정을 제거하고 분리 실행 코드를 복구한다.
2. Gradle 태스크 및 문서 실행 방법을 분리 구조로 맞춘다.
3. 포맷/테스트/빌드/문서 검사 통과 후 계획 문서를 완료 처리한다.

## 위험 및 롤백
- 위험: 분리 복구 과정에서 실행 태스크 또는 토큰 로딩이 누락될 수 있음
- 대응: `runDiscordBot` 직접 실행 검증과 문서 동기화
- 롤백: 통합 lifecycle 커밋으로 복귀

## 검증
- [x] 빌드 (`./gradlew build`)
- [x] 테스트 (`./gradlew test`)
- [x] 계약 검사 (`./scripts/check-agent-docs.sh`)

## 결정 로그
- 2026-04-27 15:25:53: 사용자 요청에 따라 봇 실행 구조를 Spring 통합에서 분리 실행으로 되돌리기로 결정
- 2026-04-27 15:28:00: `runDiscordBot`/`runBot` 태스크 복구와 `DiscordBotMain` 엔트리포인트 재도입 완료
