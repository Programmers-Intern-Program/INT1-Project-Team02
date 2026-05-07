# 2026-05-07-153534-meeting-start-context-codex

- 작성자: codex
- 상태: 완료(읽기 전용)
- 생성시각(로컬): 2026-05-07 15:35:34

## Background

Context assembly currently mixes project memory and question retrieval into a single long-term context.

## Goal

Split context output into meeting start context, current meeting short-term context, and question-specific context.

## Scope

- Wire `MeetingStartContextProvider` into `ContextService`.
- Replace `LongTermContext` with `QuestionContext`.
- Add start context invalidation on `MeetingEndedEvent`.
- Update internal API contract docs and focused tests.

## Checkpoints

1. DTO and repository support.
2. Service and prompt rendering changes.
3. Tests and docs.

## Verification

- [x] `./gradlew test`
- [x] `./gradlew spotlessCheck`

## Decision Log

- 2026-05-07 15:35: Use in-memory start context only; Redis/DB snapshots are out of scope.
- 2026-05-07 15:35: Keep `unresolvedItems` as one raw string from the latest past summary with non-blank unresolved items.
- 2026-05-07 15:54: Completed implementation and verification.
