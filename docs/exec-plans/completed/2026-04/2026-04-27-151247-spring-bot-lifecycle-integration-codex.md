# 2026-04-27-151247-spring-bot-lifecycle-integration-codex

- 작성자: codex
- 상태: 완료(읽기 전용)
- 생성시각(로컬): 2026-04-27 15:12:47

## 배경
- Discord 봇을 별도 main(`DiscordBotMain`)으로 실행하는 구조는 학습/운영 진입점이 이원화되어 혼동을 준다.
- 사용자 요청에 따라 `FlodiBackApplication` 단일 진입점에서 봇과 API를 함께 구동하는 구조로 통합이 필요하다.

## 목표
- Spring Boot 시작 시 Discord JDA 클라이언트를 자동 기동한다.
- 애플리케이션 종료 시 Discord 연결을 안전하게 정리한다.
- 실행 가이드를 `bootRun` 중심으로 단순화한다.

## 범위
- `DiscordBotMain` 분리 실행 제거 및 Spring 라이프사이클 컴포넌트 도입
- Discord 설정 키(`discord.bot.*`) 추가
- 빌드 태스크 정리(`runDiscordBot`/`runBot` 제거) 및 문서 갱신

## 비범위
- STT 연동
- Internal API 전송 파이프라인
- 봇 명령 기능 확장

## 단계
1. 봇 초기화/종료를 담당하는 Spring 컴포넌트를 추가한다.
2. 기존 분리 실행 엔트리포인트와 관련 태스크/문서를 정리한다.
3. 포맷/테스트/빌드/문서 검증을 수행하고 계획 문서를 완료 처리한다.

## 위험 및 롤백
- 위험: 토큰 누락 시 애플리케이션 전체 기동 실패 가능
- 대응: `discord.bot.enabled`로 봇 기동을 명시 제어하고 누락 시 명확한 예외 메시지를 제공
- 롤백: 신규 lifecycle 컴포넌트를 제거하고 `DiscordBotMain` 분리 실행 구조로 복귀

## 검증
- [x] 빌드 (`./gradlew build`)
- [x] 테스트 (`./gradlew test`)
- [x] 계약 검사 (`./scripts/check-agent-docs.sh`)

## 결정 로그
- 2026-04-27 15:12:47: 단일 진입점 요구사항에 따라 봇 실행을 Spring lifecycle로 통합하기로 결정
- 2026-04-27 15:15:30: 테스트/로컬 기동 안정성을 위해 `discord.bot.enabled` 기본값을 `false`로 두고 명시 활성화 방식으로 채택
- 2026-04-27 15:17:20: `.env.example` 내 실제 토큰 문자열을 제거하고 예시값으로 교체해 시크릿 노출 위험 제거
