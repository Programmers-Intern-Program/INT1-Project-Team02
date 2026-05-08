# 2026-05-08-114000-discord-ephemeral-dashboard-codex

- author: codex
- status: completed
- created-at: 2026-05-08 11:40:00

## Background

The Discord panel should behave less like a channel-pinned control surface and more like a personal interaction panel. Discord text messages cannot directly create ephemeral replies, so the text command will provide a temporary launcher and component interactions will stay ephemeral.

## Goals

- Replace project view/status split with a single dashboard action.
- Remove the panel refresh action and channel panel tracking.
- Keep project creation as a modal.
- Make the interactive panel and no-project meeting confirmation ephemeral after the launcher click.
- Leave only shared meeting state changes in the channel.

## Scope

- Discord bot listener UI behavior only.
- No backend API contract changes.

## Validation

- [x] `./gradlew test`
- [x] `./gradlew spotlessCheck`
- [x] `./gradlew build`

## Decision Log

- 2026-05-08 11:40:00: Use a temporary public launcher for `!flodi` because plain message commands cannot reply ephemerally.
- 2026-05-08 11:55:00: Dashboard, project selection, and no-project meeting confirmation now stay in ephemeral interaction responses.
