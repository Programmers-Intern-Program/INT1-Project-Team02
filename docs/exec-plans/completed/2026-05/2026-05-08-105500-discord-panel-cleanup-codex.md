# 2026-05-08-105500-discord-panel-cleanup-codex

- author: codex
- status: completed
- created-at: 2026-05-08 10:55:00

## Background

The Discord component panel works, but team feedback asked for calmer button colors, less channel noise, and a panel that behaves like a single reusable control surface.

## Goals

- Keep one panel per channel during a bot process lifetime.
- Add a refresh button that moves the panel to the latest channel position.
- Make non-critical guidance messages temporary.
- Use neutral button styles except for meeting start/end.

## Scope

- Discord bot listener UI behavior only.
- No backend API contract changes.

## Validation

- [x] `./gradlew test`
- [x] `./gradlew spotlessCheck`
- [x] `./gradlew build`

## Decision Log

- 2026-05-08 10:55:00: Discord does not support sticky messages, so use one tracked panel per channel plus a refresh button.
- 2026-05-08 11:31:00: Keep meeting start/end visually distinct and make auxiliary channel guidance temporary to reduce noise.
