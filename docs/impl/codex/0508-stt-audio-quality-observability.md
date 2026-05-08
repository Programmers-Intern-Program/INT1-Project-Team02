---
generated-by: ai-draft
reviewed-by:
reviewed-at:
evidence:
---

# STT 오디오 품질 관측 보강

> Discord 음성 수신 중 DAVE 복호화 실패와 짧은 오디오 전사 품질을 해석할 수 있도록 STT 세션 단위 관측 로그를 보강했다.

- 구현 일자: 2026-05-08
- 작성자: codex
- 담당 파트: Discord bot STT pipeline
- 관련 테이블: 없음

AI가 초안을 작성할 때는 `reviewed-by`, `reviewed-at`, `evidence`를 비워둡니다.
개발자가 사실관계와 근거를 검토한 뒤 직접 채웁니다.

---

## 왜 이렇게 구현했나

### 문제 상황
Discord 음성 수신 로그에서 `DaveDecryptor`와 `AudioPacket` 복호화 WARN이 반복되지만, STT 저장 자체는 성공하는 상황이 있었다.
기존 로그만으로는 복호화 실패가 실제 발화 품질에 영향을 주는지, 짧은 오디오가 헛전사를 만드는지 판단하기 어려웠다.

### 선택한 방식
수신 핸들러가 화자별 복호화 실패, 최근 실패 여부, 전송된 PCM 프레임/바이트, VAD 점수를 세션 시작부터 누적한다.
OpenAI Realtime 최종 전사 이벤트가 도착하면 `BotSttListener`가 세션 품질 스냅샷과 전사 길이를 한 줄의 품질 로그로 남긴다.

### 고려했던 다른 방법들
| 방식 | 장점 | 단점 | 출처/근거 | 선택 여부 |
|------|------|------|-----------|----------|
| final 결과에서 품질 로그만 남김 | 구현이 단순하고 저장 로직에 영향이 없다 | 정책 판단에는 쓰기 어렵다 | 로컬 로그 분석 | 선택 |
| 짧은 오디오/복호화 실패 세션을 즉시 차단 | 헛전사를 줄일 수 있다 | 실제 짧은 발화를 누락할 수 있다 | 로컬 로그 분석 | 미선택 |
| 외부 라이브러리 WARN만 숨김 | 로그 소음은 줄어든다 | 품질 원인을 추적할 근거가 사라진다 | 로컬 로그 분석 | 미선택 |

### 이 방식을 선택한 이유
현재 단계에서는 정책 변경보다 관측 가능성 확보가 우선이다.
STT 저장 성공 여부와 오디오 품질 지표를 함께 보아야 DAVE 실패가 네트워크 노이즈인지, 실제 품질 저하인지 분리할 수 있다.

---

## 핵심 구현 내용

### 구조 설명
`PerUserAudioReceiveHandler`는 화자별 STT 세션에 품질 지표를 누적한다.
`OpenAiRealtimeEventHandler`는 OpenAI로 전송된 PCM 바이트를 기준으로 전송 오디오 길이를 계산해 `SttResult`에 담는다.
`BotSttListener`는 final 결과를 받을 때 품질 스냅샷을 읽어 `[STT/품질판정]` 로그를 남긴다.

### 핵심 코드
```java
new BotSttListener(
        meetingId,
        session.speakerId,
        speakerName,
        captionChannel,
        () -> buildQualitySnapshot(userId, session));
```

```java
new SttResult(
        session.sessionId(),
        session.speakerId(),
        finalText,
        true,
        0L,
        0L,
        sentPcmBytes,
        audioDurationMs(sentPcmBytes),
        null);
```

### 설계 결정사항
- 품질 로그는 저장/전송 여부를 결정하지 않는다: 짧은 실제 발화를 누락하지 않기 위해 관측용으로만 둔다.
- 세션 시작/종료 시점의 복호화 실패 카운터를 비교한다: 발화 중 실패가 실제 final 품질과 겹치는지 확인하기 위해서다.
- VAD 점수와 전송 PCM 바이트를 함께 남긴다: 낮은 VAD 점수와 짧은 오디오가 헛전사와 관련 있는지 판단하기 위해서다.

---

## 어려웠던 점 & 해결 방법

### 문제 1: 외부 DAVE WARN과 내부 품질 지표가 같은 의미가 아님
- 상황: `DaveDecryptor`/`AudioPacket` WARN은 JDA 내부 복호화 단계에서 발생하고, 수신 핸들러의 `decodeFailures`는 핸들러로 전달된 `OpusPacket` 처리 결과를 센다.
- 시도한 것: Docker 로그에서 외부 WARN, encoded packet 샘플, STT final 품질 로그를 별도 집계했다.
- 해결: final 품질 로그에 세션 단위 내부 지표를 남기고, 외부 WARN은 별도 로그 레벨 정책으로 다룰 수 있게 분리했다.

---

## 다음에 개선할 점

- [ ] STT 원문을 기본 INFO 로그에서 제거하고 명시적 디버그 플래그가 있을 때만 남긴다.
- [ ] `club.minnced.discord.jdave` DEBUG를 짧게 켜서 DAVE transition/key ratchet 시점과 복호화 실패 시점을 비교한다.
- [ ] 사용자별 DAVE 실패율을 직접 집계해 특정 화자/네트워크 문제인지 더 쉽게 판단한다.
- [ ] Redis/PostgreSQL compose 포트 바인딩을 localhost로 제한해 로컬 개발 보안을 강화한다.

---

## 포트폴리오 메모

> Discord DAVE 복호화 WARN이 STT 품질 문제인지 단순 네트워크 노이즈인지 판단하기 어려운 문제를 발견했다.
> 세션 단위 오디오 길이, 복호화 실패, VAD 점수, 전송 프레임 수를 함께 남기도록 관측 지표를 보강했다.
> 그 결과 후속 튜닝에서 정책 변경 전 실제 품질 저하 여부를 로그 기반으로 검증할 수 있는 근거를 마련했다.
