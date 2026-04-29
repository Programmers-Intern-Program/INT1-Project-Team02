---
generated-by: ai-draft
reviewed-by:
reviewed-at:
evidence:
---

# Discord Bot OpenAI STT 파이프라인 1차 연결

> Discord 봇이 화자별 오디오를 STT로 변환한 뒤 내부 API(`/internal/v1/speech`)로 최종 텍스트를 전달하도록 1차 end-to-end 경로를 연결했다.

- 구현 일자: 2026-04-29
- 작성자: codex
- 담당 파트: A (Discord 봇 + 실시간 음성 파이프라인)
- 관련 테이블: `utterances`

---

## 왜 이렇게 구현했나

### 문제 상황
- 기존 코드는 오디오 수신 통계까지만 있었고, STT 최종 텍스트를 내부 저장 API로 전달하는 연결이 없었다.

### 선택한 방식
- `SttProvider` 인터페이스 추상화는 유지하고, OpenAI Realtime 구현은 `provider/openai` 패키지 내부에 한정했다.
- 봇 수신 핸들러(`PerUserAudioReceiveHandler`)에서 화자별 세션을 시작/전송/종료하고, 결과 소비는 `BotSttListener`로 분리했다.

### 고려했던 다른 방법들
| 방식 | 장점 | 단점 | 출처/근거 | 선택 여부 |
|------|------|------|-----------|----------|
| 봇 핸들러에서 바로 HTTP 저장 호출 | 구조 단순 | STT provider 교체/테스트 어려움 | 코드 구조 검토 | 미선택 |
| STT 결과 소비자 분리(`BotSttListener`) | 책임 분리, 교체 용이 | 클래스 수 증가 | 코드 구조 검토 | 선택 |

### 이 방식을 선택한 이유
- A 역할 범위(오디오 수집/변환/전달)를 빠르게 완성하면서도, 이후 Whisper/AWS 등 provider 교체를 쉽게 하기 위해서다.

---

## 핵심 구현 내용

### 구조 설명
- `DiscordBotMain`: 봇 부트스트랩, env 설정, provider 주입.
- `DiscordCommandListener`: `!join/!leave`에서 수신 핸들러/STT 세션 수명주기 관리.
- `PerUserAudioReceiveHandler`: Opus 디코딩 PCM을 화자별 STT 세션으로 전송, 무음/leave 시 세션 종료.
- `BotSttListener`: `isFinal=true` 결과만 `/internal/v1/speech`로 비동기 POST.
- `OpenAiRealtime*`: WebSocket 연결, `session.update`, `append`, `commit`, `completed/delta/error` 이벤트 처리.

### 설계 결정사항
- 발화 경계는 1차로 `1500ms` 무음 기준을 사용했다.
- `isFinal=false(delta)`는 저장하지 않고 `isFinal=true(completed)`만 저장한다.
- 로그에는 원문 텍스트를 남기지 않고 길이만 기록한다.

---

## 어려웠던 점 & 해결 방법

### 문제 1: `userPackets=0`인데도 음성 수신은 되는 상황
- 상황: 환경에 따라 `UserAudio` 콜백이 거의 오지 않는 반면, `OpusPacket`은 정상 수신되는 케이스가 있었다.
- 시도한 것: `canReceiveEncoded()` 경로에서 디코딩 가능 여부를 별도 집계했다.
- 해결: STT PCM 전송 경로를 `handleEncodedAudio`의 디코딩 결과 기준으로 잡고, `UserAudio`는 이름/보조 통계 용도로 유지했다.

---

## 다음에 개선할 점

- [ ] 완전 무음 상태에서도 즉시 세션 종료되도록 타이머 기반 silence watcher 추가
- [ ] PCM 24k mono 정규화(`OpenAiPcmConverter`) 구현
- [ ] 길드/채널별 동적 meeting 매핑(현재는 기본 meetingId 1개 사용)

---

## 포트폴리오 메모

> Discord 음성 스트림을 화자별로 분리 수신하는 단계에서, 환경별 오디오 콜백 차이로 인해 STT 연계가 누락되는 문제를 확인했다.
> 이를 Opus 디코딩 기반 세션 라우팅 구조로 재설계하고 OpenAI Realtime STT와 내부 저장 API를 연결해 end-to-end 파이프라인을 완성했다.
