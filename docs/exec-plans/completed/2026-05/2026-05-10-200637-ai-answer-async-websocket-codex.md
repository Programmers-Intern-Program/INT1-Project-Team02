# 2026-05-10-200637-ai-answer-async-websocket-codex

- author: codex
- status: completed
- created-at: 2026-05-10 20:06:37

## Background

Meeting speech ingest currently waits for AI answer generation when a wake word is present. That couples `/internal/v1/speech`, caption delivery, RAG context assembly, embedding calls, and GLM latency.

## Goal

Decouple speech ingest from AI answer generation, deliver AI answers to the web frontend through WebSocket events, and reduce average GLM latency by trimming prompt/RAG context.

## Scope

- Make AI answer generation asynchronous for wake-word speech only.
- Add WebSocket AI answer events for `PENDING`, `COMPLETED`, and `FALLBACK`.
- Add dedicated async executor and pending scheduler.
- Add embedding/GLM timeout settings and output token limit.
- Trim short-term context and prompt text sizes.
- Add latency logs without logging speech text.

## Checkpoints

1. Add async answer event DTO/service/configuration.
2. Refactor speech ingest to trigger async answers only when a question is extracted.
3. Trim ContextService prompt/RAG inputs and repository queries.
4. Add timeout/token settings and latency logs.
5. Update tests and verify focused Gradle tests.

## Risks

- WebSocket event contract must stay simple enough for frontend consumption.
- Async rejection must not block `/speech`.
- Late `PENDING -> COMPLETED/FALLBACK` ordering must be deterministic enough for frontend state updates.

## Verification

- [x] Focused speech service/controller tests
- [x] Focused context service tests
- [x] GLM client tests
- [x] Build or broader test suite when feasible

## Decision Log

- 2026-05-10 20:06:37: Use WebSocket as the first AI answer delivery path; Discord bot delivery is out of scope for this change.
- 2026-05-10 20:06:37: Keep GLM timeout at 15s with retry 0; use 5s `PENDING` event for visible responsiveness.
- 2026-05-10 20:06:37: Skip ContextService parallelization until latency logs prove DB/RAG is the main bottleneck.
- 2026-05-10 20:24:00: Completed implementation and verified with `./gradlew test`, `./gradlew spotlessCheck`, and `./gradlew build`.
