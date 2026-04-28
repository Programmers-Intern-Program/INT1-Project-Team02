# Discord Bot (JDA + DAVE) 실행 가이드

## 배경
- Discord 음성 연결은 2026-03-01부터 DAVE(E2EE) 지원이 필수입니다.
- 이 저장소는 JDA + JDAVE 조합으로 음성 채널 연결과 화자별 수신 기본 골격을 제공합니다.

## 준비
1. Discord Developer Portal에서 봇 생성 및 토큰 발급
2. 봇 권한 부여: `View Channels`, `Send Messages`, `Read Message History`, `Connect`, `Speak`
3. 로컬 환경변수 설정

```bash
export DISCORD_TOKEN=발급받은_토큰
export DISCORD_BOT_PREFIX=!
```

또는 루트의 `.env.example`을 참고해 `.env`를 구성합니다.
봇은 시스템 환경변수 우선, 없으면 루트 `.env` 값을 fallback으로 읽습니다.

## 실행
JDAVE는 네이티브 접근 경고 제거를 위해 JVM 옵션이 필요합니다.
`runDiscordBot` 태스크는 해당 옵션을 포함합니다.

```bash
./gradlew runDiscordBot
```

## Apple Silicon(M1/M2/M3)에서 `No decoder available`가 나올 때
macOS arm64에서 로컬 JVM으로 실행하면 Opus 네이티브 디코더 로딩이 실패할 수 있습니다.
이 경우 Linux arm64 컨테이너에서 봇을 실행합니다.

1. 루트 `.env`에 토큰이 있는지 확인
2. 기존 로컬 봇 프로세스 종료
3. 아래 명령으로 컨테이너 실행

```bash
docker compose -f docker-compose.bot.yml up --force-recreate
```

종료:

```bash
docker compose -f docker-compose.bot.yml down
```

## 명령어
- `!ping`: 봇 상태 확인
- `!join`: 명령 실행자가 들어간 음성 채널로 봇 입장
- `!leave`: 음성 채널 퇴장
- `!stats`: 화자별 수신 패킷/바이트 통계 확인

## 구현 메모
- 엔트리포인트: `com.flodiback.bot.DiscordBotMain`
- 명령 처리: `com.flodiback.bot.command.DiscordCommandListener`
- 화자별 수신 핸들러: `com.flodiback.bot.audio.PerUserAudioReceiveHandler`
- DAVE 설정: `AudioModuleConfig.withDaveSessionFactory(new JDaveSessionFactory())`
