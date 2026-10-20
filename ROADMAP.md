# ENotes Implementation Roadmap

Status rule: only mark a feature **verified** after implementation and a relevant automated/device test. A successful APK build proves compilation/package creation only; it does not prove runtime behavior.

## Baseline (2026-10-20)
- [x] Repository and `AGENTS.md` inspected.
- [x] Product requirements reviewed in `docs/complete-product-roadmap.md`, `docs/screen-inventory.md`, `docs/design-system.md`, `docs/backend-and-integrations.md`, `docs/innovation-and-multitasking.md`, and `docs/testing-and-release-checklist.md`.
- [x] Existing Android APK workflow run #13 succeeded at commit `7096e237bcae2a4b7fd98c7d4133d238f4391156`.
- [x] Debug APK artifact `enotes-debug-apk` exists for run #13 (16,064,906 bytes; artifact expires 2026-11-03).
- [ ] Baseline behavior verified on an Android emulator/device.
- [ ] Automated unit/instrumentation tests exist and pass.

## V1 — reliable local notes (current priority)
- [x] Local Room persistence and note create/edit.
- [x] Active, archived, and trash note lists.
- [x] Search title/body and sort active notes.
- [x] Pin, archive/unarchive, trash/restore, permanent delete confirmation.
- [x] Share a note through Android Sharesheet.
- [x] JSON backup export and document-picker import with format validation and ID-based merge.
- [x] Plain-text export.
- [ ] Exercise all note lifecycle flows on a device.
- [ ] Add automated tests for repository and backup parsing.
- [ ] Harden backup import: validate required fields/IDs and avoid unbounded memory reads.
- [x] Add settings-backed light/dark/system theme selection (implementation committed; pending CI build and runtime verification).
- [ ] Improve home/editor visual hierarchy, accessibility labels, empty states, and editor draft safety.

## V1.1 — organization and documents
- [ ] Folders and tags with Room schema migration and data-preserving tests.
- [ ] Favorites/collections and filterable universal search.
- [ ] Document library using Android's Storage Access Framework.
- [ ] PDF viewing and external document opening; no broad storage permission unless required.
- [ ] Attachments linked to notes.

## V1.2 — planning and privacy
- [ ] Reminders and calendar/task views with permission-denied and notification-disabled handling.
- [ ] Optional app lock using Android BiometricPrompt and secure PIN storage.
- [ ] Settings, accessibility, recovery, and data-management flows.

## V2 — connected services (must be real integrations, not mock buttons)
- [ ] Authentication and cross-device sync through a documented secure backend.
- [ ] Conflict-safe sync; retain local drafts and surface merge conflicts.
- [ ] Google Drive OAuth integration, least-privilege scopes, and explicit confirmation before remote mutations.
- [ ] AI study tools via a configurable secure backend/provider; no API secrets in the Android client.
- [ ] Source-linked answers, summaries, flashcards, quizzes, and knowledge map with clear data provenance.

## Release / delivery
- [x] GitHub Actions debug APK build workflow and artifact upload.
- [ ] Add Gradle wrapper and pin build tooling reproducibly.
- [ ] CI build + automated tests on each pull request.
- [ ] Device/emulator smoke tests: launch, create/edit/search, archive/trash/restore, backup export/import, theme.
- [ ] Verify release signing and release APK/AAB separately from debug artifacts.
- [ ] Review privacy policy, data deletion/export, permissions, and store readiness.

## Current honest release status
The repository has a buildable **prototype**, not the complete product. The latest verified artifact is a debug APK from commit `7096e237`. Features above remain unchecked until they are implemented and tested. Cloud sync, Google Drive, and AI require backend/provider configuration and user consent.