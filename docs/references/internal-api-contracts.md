# 내부 API 계약

이 문서는 봇-백엔드 연동에 중요한 내부 API 계약을 기록합니다.

## `POST /internal/v1/speech`
Discord 봇 파이프라인에서 STT 변환 결과를 수신합니다.

요청 본문:
```json
{
  "meeting_id": "...",
  "speaker_discord_id": "...",
  "speaker_name": "김철수",
  "text": "이번 스프린트 목표를 어떻게 잡을까요?",
  "timestamp": "2026-04-23T10:30:00"
}
```

응답 본문:
```json
{
  "resultCode": "200-1",
  "msg": "발화가 저장되었습니다.",
  "data": {
    "utterance_id": 11,
    "meeting_id": 1,
    "ai_answer": "네, 기존 결정사항 기준으로 인증 방식은 JWT를 사용하기로 했습니다."
  }
}
```

- `ai_answer`는 호출어가 감지되어 AI 답변이 생성된 경우에만 문자열로 내려갑니다.
- 호출어가 없거나 AI 답변 생성에 실패하면 `ai_answer`는 `null`입니다.

## `GET /internal/v1/projects/{id}/context`
에이전트/봇 워크플로에서 사용할 프로젝트 컨텍스트를 조회합니다.

## `PUT /internal/v1/projects/{id}/context`
프로젝트 컨텍스트를 갱신합니다.

## `POST /internal/v1/discord/connect`
Discord 서버와 프로젝트를 연결합니다.

## `GET /internal/v1/discord/{server_id}/project`
Discord 서버 ID로 연결된 프로젝트를 조회합니다.

## `POST /internal/v1/meetings/{id}/summary`
회의 요약(초안/확정)을 저장합니다.

## `POST /internal/v1/meetings/{id}/decisions`
추출된 결정사항을 저장합니다.

## `POST /internal/v1/meetings/{id}/worklogs`
추출된 작업 로그를 저장합니다.

## 변경 규칙
- 계약이 바뀌면 이 문서를 반드시 갱신하고 실행 계획 문서에 변경 내역을 남깁니다.
- 하위 호환이 깨지는 변경은 마이그레이션 절차와 봇 연동 조정 사항을 함께 기록합니다.
