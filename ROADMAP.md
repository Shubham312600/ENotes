# ENotes Implementation Roadmap

Status rule: only mark a feature **verified** after implementation and a relevant automated/device test. A successful APK build proves compilation/package creation only; it does not prove runtime behavior.

## Baseline (2026-10-20)
- [x] Repository and `AGENTS.md` inspected.
- [x] Product requirements reviewed in `docs/complete-product-roadmap.md`, `docs/screen-inventory.md`, `docs/design-system.md`, `docs/backend-and-integrations.md`, `docs/innovation-and-multitasking.md`, and `docs/testing-and-release-checklist.md`.
- [x] Existing Android APK workflow run #13 succeeded at commit `7096e237bcae2a4b7fd98c7d4133d238f4391156`.
- [x] Latest Android APK workflow run #17 succeeded at commit `9cd3534e4abe9937fde8af5f3c3f8e63e18de0d0` (2026-10-20).
- [x] Dashboard Android APK workflow run #18 succeeded for implementation commit `be3b8da`; debug APK artifact is 16,112,842 bytes and expires 2026-11-03.
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

## Home dashboard implementation (2026-10-20; verification pending)
- [x] Replaced the plain Notes header with ENotes branding/tagline, explicit local-only sync status, notification/account status entry points, and reference-inspired lavender cards.
- [x] Added five visible quick actions: create note, create folder, import a document as a note attachment, voice note, and AI assistant.
- [x] New folders persist in Room; notes can be assigned to built-in categories and folders.
- [x] Category and folder counts/filters and recent note cards use real Room data; search includes title, body, category, and folder.
- [x] Added sorting, attachment opening, and a creation menu on the FAB while preserving Archive, Trash, and Settings destinations.
- [x] Added a Room 1→2 migration for category, folder, attachment URI, and folder records; legacy notes are retained and default to “Others”.
- [x] Backup import/export preserves the new category and folder metadata while remaining backward compatible with V1 backups.
- [x] Android debug build and artifact upload passed in GitHub Actions run #18; this machine has no JDK, Gradle, or Android SDK for a local build.
- [ ] Automated tests are not configured in this repository or current workflow.
- [ ] Verify migration, navigation, document attachment, and visual layout on an emulator/device; no emulator is configured here.
- [ ] Voice recording and AI remain unavailable and explicitly say so; account/cloud sync and notification/reminders remain unconfigured.
- [ ] Compare a runtime screenshot with the supplied reference; source implementation alone does not establish visual parity.

## Dashboard correction pass (2026-10-20; CI passed, runtime verification pending)
- [x] Reworked Notes into one vertically scrolling dashboard and made five actions and five category shortcuts responsive, eliminating the clipped horizontal strips.
- [x] Added a separate filter control for category, folder, pinned-only, and title sorting; kept counts bound to active Room notes.
- [x] Restyled recent notes as bordered cards with document tiles, category/folder metadata, timestamps, attachment access, and supported overflow actions.
- [x] Added a circular purple creation FAB, a working More destination, and the subtle footer treatment while retaining Archive, Trash, and Settings.
- [x] Fixed document import naming by reading `OpenableColumns.DISPLAY_NAME`; imported files now remain attachments with an empty note body.
- [x] Added a targeted Room 2→3 data-preserving repair for the exact numeric-title/boilerplate-body attachment record created by the old importer. Its attachment URI and category are retained; category is not silently reassigned.
- [x] Checked the prior PR CI: run #20 passed at previous branch head `565f97a` (Android debug build and APK artifact upload).
- [x] Correction build passed at branch head `6ec6a3e` (workflow run ID `39394896484`); APK artifact `enotes-debug-apk` uploaded (16,174,472 bytes; expires 2026-11-03).
- [x] GitHub Actions build/upload steps all succeeded; this machine has no JDK, Gradle, or Android SDK for a local build.
- [ ] No automated test suite is configured; no emulator is available for migration/navigation/interaction tests or a screenshot comparison.
- [ ] Screenshot parity remains unverified until an app screenshot is captured and compared at the supplied device size.

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
The repository has a buildable **prototype**, not the complete product. The dashboard correction has a successful debug APK build and uploaded artifact. Runtime behavior and screenshot parity remain unchecked until verified on a device. Cloud sync, Google Drive, voice recording, and AI require provider configuration; account and reminder features are not connected.

