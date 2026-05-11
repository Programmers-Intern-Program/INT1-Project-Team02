# 회의 중 AI 답변 비동기 WebSocket 전달

> `/internal/v1/speech` 저장 응답과 AI 답변 생성을 분리하고, 웹 프론트엔드가 `/topic/meetings/{meetingId}/ai-answer`로 AI 답변 상태를 받을 수 있게 했다.

## 배경

호출어가 포함된 발화는 기존에 발화 저장 요청 안에서 컨텍스트 조립, embedding, GLM 호출까지 기다렸다. 이 구조에서는 GLM 지연이 내부 API 응답과 caption 흐름까지 지연시킬 수 있었다.

## 구현

- 발화 저장 후 question이 추출될 때만 `SpeechAiAnswerAsyncService`를 실행한다.
- AI 답변은 `PENDING`, `COMPLETED`, `FALLBACK` WebSocket 이벤트로 전달한다.
- 5초 후에도 답변이 끝나지 않으면 별도 scheduler가 `PENDING`을 발행하고, 이후 GLM 완료/실패에 따라 후속 이벤트를 발행한다.
- GLM timeout은 15초, retry는 0으로 조정했고, completion token 상한을 256으로 둔다.
- 최근 발화와 question context 개수를 줄이고 긴 텍스트를 잘라 prompt 크기를 제한한다.

## 검증

- focused speech/context/GLM tests
- `InternalSpeechControllerTest`

## 리뷰 메타

- reviewed-by:
- reviewed-at:
- evidence:
