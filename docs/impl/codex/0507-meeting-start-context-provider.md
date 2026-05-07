---
generated-by: ai-draft
reviewed-by:
reviewed-at:
evidence:
---

# Meeting Start Context Provider

> Split meeting answer context into cached meeting-start memory, current-meeting short-term memory, and question-specific retrieval memory.

- implementation-date: 2026-05-07
- author: codex
- related-part: meeting context, speech answer prompt
- related-tables: meetings, decisions, meeting_summaries, work_logs, context_cache

---

## Implementation Notes

### Problem
The previous context response mixed project metadata, default long-term memory, and question retrieval results into one `LongTermContext`. That made it hard to distinguish stable meeting-start background from memories fetched specifically for a question.

### Approach
`ContextResponse` now exposes `startContext`, `shortTerm`, and `questionContext`. `MeetingStartContextProvider` builds and caches the meeting-start memory in memory, while `ContextService` keeps current meeting context and question retrieval separate.

### Key Decisions
- `MeetingStartContext` uses fixed count limits instead of token budgeting in this phase.
- `unresolvedItems` remains a single raw string from the latest past summary with non-blank unresolved items.
- Blank questions skip Decision/MeetingSummary retrieval and return an empty `QuestionContext`.
- Meeting end invalidation is handled by an `AFTER_COMMIT` event listener instead of adding provider coupling to `MeetingService`.

---

## Follow-Ups

- [ ] Consider Redis or DB-backed start context snapshots if multi-instance deployment requires shared cache state.
- [ ] Add token budgeting for `startContext` if recent memory grows beyond the fixed limits.
- [ ] Normalize unresolved items and action items into first-class retrieval sources.
