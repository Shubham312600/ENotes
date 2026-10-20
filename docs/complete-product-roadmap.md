# ENotes — Complete Product and Engineering Roadmap

Status: master planning document. It describes intended scope; it does not claim these features are implemented.

## 1. Product vision
ENotes is an Android-first, privacy-conscious notes and document workspace. It should be fast and useful offline for core note-taking, support organized documents and reminders, and offer optional account sync, Google Drive access, and AI assistance through properly configured services.

## 2. Product principles
- Offline-first core notes; clear online-only indicators for cloud and AI capabilities.
- Local-first user experience with transparent account/sync behavior.
- No silent destructive changes, including AI-suggested actions, Drive changes, deletes, and restore-overwrites.
- Honest capability and file-format labels; never fake success or source citations.
- Minimum necessary permissions and native Android system interfaces.
- Accessibility, light/dark/system appearance, scalable text, and recoverable error states.
- Keep secrets server-side; do not ship AI or OAuth client secrets in the app.
- Ship in testable vertical slices, not 80 disconnected mock screens.

## 3. User and feature areas
1. Startup, onboarding, permission education, privacy notice, and app lock.
2. Home, navigation, activity inbox, global search, recent and pinned items.
3. Notes editor, read mode, autosave, folders, tags, templates, archive, trash, bulk actions.
4. Local documents, attachments, metadata, preview, external viewer handoff, import and storage.
5. Google Drive authorization, browsing, search, file details/open, connection settings and recovery.
6. AI landing/chat, source-linked answers, file search, document result cards, action proposals, confirmations, outcomes and provider status.
7. Reminders, repeat schedules, calendar, notification actions and permissions.
8. Account, sync status/conflicts, backup/restore, privacy controls, settings, help and diagnostics.
9. Cross-cutting loading, empty, offline, error, permission, unsaved-change, accessibility and migration states.

Detailed screen IDs are in `screen-inventory.md`.

## 4. Phased delivery plan and exit criteria

### Phase 0 — Repository and architecture
Tasks:
- Select and document Android stack, minimum supported Android version, build tooling, package name and architecture.
- Create app module, dependency management, navigation, theme tokens, local persistence, lint/test setup and CI build.
- Update README with prerequisites and exact run/build commands.
Exit criteria:
- A clean checkout builds in the documented environment.
- App launches into a real screen.
- CI or documented local checks can be repeated.

### Phase 1 — First useful notes app
Tasks:
- Home and notes list; create/read/edit/delete note.
- Persist notes locally; autosave/draft recovery; sort, search, pin/favorite.
- Folders and tags; archive and trash with restore; empty/loading/error states.
- Shared dialogs and accessible controls.
Exit criteria:
- Notes survive app restarts.
- Create/edit/search/organize/restore flows pass tests.
- No accidental silent deletion or loss of unsaved edits.

### Phase 2 — Design and navigation expansion
Tasks:
- Implement common app shell, navigation destinations, list/grid options, templates, recent items, activity inbox and bulk selection.
- Build representative screen set and review screenshots at multiple font sizes and both themes.
Exit criteria:
- Navigation graph and screen IDs map to working destinations or clearly marked future scope.
- Shared components are reused; no dead-end controls.

### Phase 3 — Documents and attachments
Tasks:
- Use Android system document picker; import supported local files.
- Show progress, result, duplicate handling, metadata and source.
- Support only tested in-app preview formats; otherwise offer external viewer handoff.
- Add extraction/indexing status only for actually supported formats and safe storage cleanup.
Exit criteria:
- Unsupported formats fail gracefully and do not corrupt data.
- Import cancel/failure/retry and external handoff are tested.

### Phase 4 — Reminders and calendar
Tasks:
- Create/edit/view/complete reminders, dates, times, recurrence, linked notes and calendar.
- Notification permission explanation and recovery; notification tap/action routing.
Exit criteria:
- Date/time validation and recurrence tests pass.
- Denied notifications do not break note-taking; scheduling state is accurate.

### Phase 5 — Identity, account, sync and backup
Tasks:
- Choose/configure a real backend and supported authentication method before promising account functionality.
- Define data ownership, account lifecycle, sync queue, retries, conflict handling and sign-out behavior.
- Backup/restore with preview and explicit overwrite/merge decision.
- Export and deletion controls based on actual backend capabilities.
Exit criteria:
- Auth and sync failures are visible and recoverable.
- Conflict tests prove neither version is silently lost.
- Backup integrity and restore consequences are documented.

### Phase 6 — Google Drive
Tasks:
- Register/configure OAuth and approved scopes; keep tokens secure and use supported Android authorization flows.
- Browse and search only authorized content; display available metadata and permissions.
- Open supported files in app or hand off to external viewer.
- Any file modifications require exact review and explicit confirmation.
Exit criteria:
- Real credentials and consent flow are tested in a configured environment.
- Expired consent, revoked access, offline, deleted files and insufficient permissions are handled.
- No UI claims connected or changed when the operation failed.

### Phase 7 — AI assistant
Tasks:
- Choose an AI provider and secure backend, with quotas, timeout, retry, privacy notice and usage/error handling.
- Implement chat, streaming if supported, draft preservation, cancellation if supported, source-linked answers only when source evidence exists, and authorized document search.
- Add document summaries and follow-up suggestions with clear provenance.
- AI action proposals must show exact changes and require confirmation before execution.
Exit criteria:
- No API secret in app binaries/source.
- Rate limits, provider outage, offline and empty-result cases are tested.
- Sources are real and traceable; AI never invents completed actions.

### Phase 8 — Security, privacy, accessibility and polish
Tasks:
- PIN/credential and biometric app-lock integration using platform-supported APIs; define recovery safely.
- Permission settings, privacy explanations, export/deletion, diagnostics that avoid leaking sensitive content.
- Light/dark/system theme, dynamic text sizes, screen reader labels/focus, contrast, narrow-screen behavior.
- Add migration/versioning and crash/error recovery.
Exit criteria:
- Security boundaries and stored data are documented.
- Accessibility and font-scale review completed for critical flows.
- Migrations preserve data and have recovery tests.

### Phase 9 — Release readiness
Tasks:
- Full unit/integration/UI test suite, clean release build, dependency/security review, privacy/data-flow review, permissions audit, app icon/metadata, versioning and release notes.
- Verify every major screen, deep link, notification action and error recovery path.
- Provide a known-limitations list and reproducible release instructions.
Exit criteria:
- Build and tests actually pass in the release environment.
- No placeholder feature is presented as working.
- Release artifact and rollback/recovery plan are verified.

## 5. Architecture decisions to settle before scaffolding
These are unresolved and must be selected based on the intended environment:
- Native Kotlin + Jetpack Compose versus another Android stack.
- Local database and search/indexing strategy.
- Backend hosting, database, authentication and synchronization protocol.
- AI provider/model, budget/quotas, privacy/data retention, and server-side secret handling.
- Google OAuth configuration, scopes and consent verification.
- Supported document preview and extraction formats.
- Minimum Android version and distribution target.

Do not fabricate credentials or select paid services without surfacing cost implications. Prefer free/local-first defaults where practical, but label external service requirements clearly.

## 6. Cross-cutting definition of done
Every implemented feature must have:
- Defined user story and acceptance criteria.
- Loading, empty, success, failure and offline behavior where applicable.
- Permission and data-access rationale.
- Accessibility semantics and both-theme review.
- Automated tests appropriate to the layer.
- Documentation for setup/configuration and limitations.
- Verified behavior; no claims based solely on a mockup.

## 7. First implementation slice
After the stack is chosen, implement:
1. App scaffold and design tokens.
2. Home and notes list.
3. Create/edit note.
4. Local persistence and autosave.
5. Search and pin.
6. Tests for persistence and core navigation.
Then review a running build and screenshots before expanding.

## 8. Status reporting format
For each phase report:
- Completed and linked files.
- Features implemented versus planned.
- Commands/checks run and actual result.
- Configuration or secrets still required from the owner.
- Known gaps, risks and next milestone.
