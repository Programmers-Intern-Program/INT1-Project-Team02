# 내부 API 계약

이 문서는 봇-백엔드 연동에 중요한 내부 API 계약을 기록합니다.

## `POST /internal/v1/speech`
Discord 봇 파이프라인에서 STT 변환 결과를 수신합니다.

요청 본문:
```json
{
  "meeting_id": 1,
  "speaker_discord_id": "123456789",
  "speaker_name": "김철수",
  "text": "이번 스프린트 목표를 어떻게 잡을까요?",
  "speech_started_at": "2026-04-23T10:30:00",
  "speech_ended_at": "2026-04-23T10:30:05"
}
```

- `speech_started_at` (필수): 봇 STT `startMs` (Unix epoch ms) 를 LocalDateTime으로 변환한 발화 시작 시각
- `speech_ended_at` (선택): 봇 STT `endMs` (Unix epoch ms) 를 LocalDateTime으로 변환한 발화 종료 시각

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

## `GET /internal/v1/meetings/{meetingId}/context`
회의 중 AI 답변용 컨텍스트를 조회합니다.

Query:
- `question` (선택): 질문 기반 추가 검색에 사용할 텍스트입니다. 비어 있으면 `questionContext`는 빈 목록을 반환합니다.

응답 `data`:
```json
{
  "startContext": {
    "meetingId": 1,
    "projectId": 10,
    "projectName": "Flodi",
    "techStack": "Spring Boot, PostgreSQL",
    "metadata": null,
    "recentDecisions": [
      {
        "id": 1,
        "content": "인증 방식은 JWT로 한다.",
        "decidedAt": "2026-05-07T10:00:00"
      }
    ],
    "recentSummaries": [
      {
        "id": 3,
        "summary": "로그인 기능 담당자를 정했다.",
        "createdAt": "2026-05-07T10:00:00"
      }
    ],
    "unresolvedItems": "API 응답 형식 미정",
    "activeWorkLogs": [
      {
        "id": 7,
        "assigneeName": "김철수",
        "task": "로그인 API 작성",
        "dueDate": "2026-05-10",
        "status": "TODO"
      }
    ]
  },
  "shortTerm": {
    "rollingSummary": "[흐름 요약]\n- ...",
    "recentUtterances": [
      {
        "speakerName": "김철수",
        "content": "인증은 JWT로 하죠.",
        "speechStartedAt": "2026-05-07T10:10:00"
      }
    ]
  },
  "questionContext": {
    "decisions": [],
    "pastSummaries": []
  }
}
```

- `startContext`는 회의 시작 시점의 프로젝트 기억이며 인메모리에서 재사용됩니다.
- `shortTerm`은 현재 회의의 rolling summary와 미압축 발화 window입니다.
- `questionContext`는 `question`이 있을 때만 Decision/MeetingSummary hybrid search 결과를 담고, `startContext`와 id 기준으로 중복 제거됩니다.

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
