# 2026-05-08-104349-discord-component-ui-codex

- 작성자: codex
- 상태: 완료(읽기 전용)
- created-at: 2026-05-08 10:43:49

## Background

The Discord bot already supports text commands for project creation and meeting control. This work adds a component-based UI entrypoint while preserving the existing command flows.

## Goals

- Add a `!flodi` panel using embeds, buttons, select menus, and modals.
- Keep project and meeting flows backed by the existing API contracts.
- Reduce channel noise by using ephemeral acknowledgements for personal UI feedback.

## Scope

- Update the Discord bot listener only.
- Keep existing text commands available.
- Do not change backend API contracts.

## Validation

- [!] `./gradlew test` - compile passed, but Testcontainers failed to initialize Docker in this environment.
- [x] `./gradlew spotlessCheck`
- [x] `./gradlew build -x test`

## Decision Log

- 2026-05-08 10:43:49: Use String Select Menu for existing project selection, Modal for project creation, and Embed for compact dashboard display.
- 2026-05-08 10:50:00: Preserve existing text command flows and add `!flodi` as the component-based entrypoint.
