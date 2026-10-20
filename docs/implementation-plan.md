# ENotes implementation plan

## Current verified starting point
The repository was inspected on 2026-10-20. The default branch is `main`; the repository contains only a minimal `README.md` and has no app source or build configuration yet. No app build can be run until a technology stack and scaffold exist.

## Decisions already requested
- Android-first product.
- Account and sync: in scope.
- Document viewing: in-app and external handoff.
- App lock: PIN/password and biometric options, subject to platform support.
- Appearance: light, dark, and system.
- Google Drive modifications: require explicit confirmation.
- Release scope: the full planned product, delivered incrementally.

## Build phases
### Phase 0 — Foundation
- Confirm or choose the Android stack before framework-specific scaffolding.
- Establish app architecture, navigation, design tokens, linting, tests, and CI/build checks.
- Add an accessible app shell and persistent local storage.
- Create a clear README with setup and run instructions.

### Phase 1 — Core notes
- Home, all notes, create/edit/read note, folders, tags, favorites, archive, trash, templates, and bulk actions.
- Implement autosave, draft recovery, search, and safe destructive confirmations.
- Add unit/UI tests for persistence and navigation.

### Phase 2 — Documents
- Local document library, Android system file picker, import progress/results, metadata, supported in-app previews, and external viewer handoff.
- Make format support explicit; do not claim unsupported extraction or preview.
- Add storage review and safe cleanup.

### Phase 3 — Reminders and navigation
- Reminder list/create/edit/detail, calendar, repeat schedules, and notification permission handling.
- Validate date/time and recurrence behavior.

### Phase 4 — Accounts and synchronization
- Sign-in only when a real supported identity/backend is configured.
- Show sync status, pending changes, failures, and conflict resolution.
- Add backup/restore previews and explicit confirmation before destructive overwrite.

### Phase 5 — Google Drive
- Implement OAuth using the supported secure flow and minimum required scopes.
- Browse/search only authorized files and show access/permission failures.
- Require explicit confirmation before any Drive modification.
- Never simulate successful authorization or file operations.

### Phase 6 — AI workspace
- Start with a provider-agnostic interface and secure backend contract.
- Support chat, source-linked answers, document result cards, and useful failure/limit states.
- Do not expose API keys in the Android client.
- Require user review and confirmation for AI-proposed actions.

### Phase 7 — Privacy, accessibility, polish
- App lock, data controls, theme/text scaling, permission recovery, offline states, diagnostics, and migration strategy.
- Verify screen-reader semantics and large-font layouts.

### Phase 8 — Release readiness
- Automated tests, clean build, navigation audit, data-loss review, dependency/security review, and release documentation.
- Report only verified outcomes.

## First vertical slice
After the Android stack is established: build the app shell + Home + local note creation/editing + persistence + tests. This validates the architecture before implementing all planned screens.
