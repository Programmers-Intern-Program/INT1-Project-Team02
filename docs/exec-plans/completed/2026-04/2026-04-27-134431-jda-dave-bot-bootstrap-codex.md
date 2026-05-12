# 2026-04-27-134431-jda-dave-bot-bootstrap-codex

- 작성자: codex
- 상태: 완료(읽기 전용)
- 생성시각(로컬): 2026-04-27 13:44:31

## 배경
- Discord 음성 연결은 2026-03-01부터 DAVE(E2EE) 대응이 필수다.
- 팀 역할 A(Discord 봇 + 실시간 음성 파이프라인) 착수를 위해 JDA 기반 최소 실행 골격이 필요하다.

## 목표
- JDA + JDAVE 기반으로 `!ping`, `!join`, `!leave` 명령이 동작하는 최소 봇을 구축한다.
- 화자별 수신 핸들러를 연결해 오디오 분리 수신 기반을 확보한다.
- 로컬 실행 가이드 문서를 추가한다.

## 범위
- Gradle 의존성/JDK Toolchain/JDAVE 런타임 네이티브 설정
- Discord 봇 엔트리포인트 및 명령 리스너 구현
- 화자별 오디오 수신 통계 핸들러 구현
- 실행 문서 추가

## 비범위
- Google STT 연동
- Spring Internal API(`/internal/v1/speech`) 전송
- 운영 배포 구성

## 단계
1. JDA/JDAVE 의존성과 실행 태스크를 설정한다.
2. Discord 봇 명령 처리 및 음성 수신 핸들러를 구현한다.
3. 빌드/테스트/포맷/문서 일관성 검증을 통과시킨다.

## 위험 및 롤백
- 위험: Java 25/JDAVE 네이티브 라이브러리 호환 문제로 음성 연결 실패 가능
- 대응: `runDiscordBot`에 `--enable-native-access=ALL-UNNAMED` 고정
- 롤백: `build.gradle.kts` 의존성/툴체인 변경과 `com.flodiback.bot` 패키지 제거

## 검증
- [x] 빌드 (`./gradlew build`)
- [x] 테스트 (`./gradlew test`)
- [x] 계약 검사 (`./scripts/check-agent-docs.sh`)

## 결정 로그
- 2026-04-27 13:44:31: Java 25 가능 환경으로 확인되어 JDAVE 경로를 기본 선택
- 2026-04-27 13:55:00: 봇 실행은 Spring 부트스트랩과 분리된 `DiscordBotMain` 엔트리포인트로 구성
- 2026-04-27 13:49:50: Java 25 전환에 따라 JaCoCo/ArchUnit을 Java 25 지원 버전으로 상향해 테스트 회귀를 복구
