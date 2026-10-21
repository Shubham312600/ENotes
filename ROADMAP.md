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
- [x] JVM tests now cover dashboard ordering and editor text-format helpers; migration/navigation/device interactions and screenshot comparison still need an emulator.
- [ ] Screenshot parity remains unverified until an app screenshot is captured and compared at the supplied device size.

## Dashboard customization and editor pass (2026-10-20; CI passed, runtime verification pending)
- [x] Replaced fixed action/category layouts with swipeable horizontal carousels, paging arrows, and page indicators.
- [x] Added saved section editing: drag to reorder, hide/show, pin to the front, remove from a displayed list, and add quick actions to Quick Access.
- [x] Quick Access can include real categories, folders, and action shortcuts; category/folder counts remain derived from active Room notes.
- [x] Replaced the basic note dialog with a responsive editor panel including selection-aware Markdown shortcuts, undo, title/word counts, category/folder, attachment link management, reminder date/time, priority, pinning, label color, and honest AI-provider fallbacks.
- [x] Added a Room 3→4 migration for note priority, reminder timestamp, and label color; backup import/export now carries these fields while remaining compatible with earlier backups.
- [x] Added JVM tests for dashboard item ordering and editor text-formatting helpers; CI now runs `testDebugUnitTest` before assembling the APK.
- [x] Fixed compile errors found by CI run #25; run #26 passed unit tests, Android debug build, and APK upload at head `00f7563` (artifact: 16,279,170 bytes; expires 2026-11-03).
- [ ] The reminder date/time is stored on the note, but notification delivery is not implemented in this prototype.
- [x] New JVM unit tests and Android debug build passed in GitHub Actions; this workstation has no JDK/Gradle/Android SDK for a local build.
- [ ] Verify interaction behavior, migration from existing databases, and screenshot layout on an emulator/device; no emulator is configured here.
- [ ] AI and voice remain unavailable without configured providers/recording support. The current one-URI note model supports one attachment per note.
- [ ] Screenshot parity remains unverified until a runtime screenshot is captured and compared with the supplied reference.

## Dashboard infographic refinement (2026-10-20; CI pending)
- [x] Framed the Quick Actions header to match the outlined section bar in the infographic; its paging arrows and Edit control remain connected to the saved carousel.
- [x] Kept Quick Access in the matching framed section bar with paging arrows and its saved Edit controls.
- [x] Added the infographic's four-benefit footer and slogan. Benefits reflow into two rows on narrow screens and one row when there is room; the existing “Better Notes ✦ Brighter Ideas” line remains as secondary branding.
- [x] Checked latest pre-refinement PR CI: run #27 passed JVM unit tests, Android debug APK build, and artifact upload at `777b9eb` (artifact: 16,279,170 bytes; expires 2026-11-03).
- [x] CI run #28 caught a Kotlin compile error because the wave color was read inside the Canvas draw callback; moved the theme color lookup into composable scope.
- [x] CI run #29 passed JVM tests, Android debug APK build, and artifact upload for the fix at `969c20c` (artifact: 16,285,082 bytes; expires 2026-11-03). This workstation has no local JDK, Gradle, or Android SDK.
- [ ] Capture and compare a runtime screenshot on a device/emulator; screenshot parity is not claimed.

## Dashboard header alignment follow-up (2026-10-20; CI passed, runtime visual check pending)
- [x] Removed the surrounding header cards and borders from Quick Actions and Quick Access to match the latest screenshot; the individual action and category tiles retain their own styling.
- [x] Set Quick Access controls to previous arrow, next arrow, then Edit at the right end, matching the reference. Quick Actions keeps the same arrows-before-Edit order.
- [x] CI run #31 passed JVM tests, Android debug APK build, and artifact upload at `eef2443` (artifact: 16,283,514 bytes; expires 2026-11-03). This workstation has no local JDK, Gradle, or Android SDK.
- [ ] Runtime screenshot comparison remains pending; no emulator is configured here.

## Editor and rich note content pass (2026-10-20; CI passed, runtime verification pending)
- [x] Made a recent-note card tap open a dedicated full-screen editor. The editor has a dark canvas, title and description fields, date and character counts, back/undo/redo/save controls, and a bottom insert toolbar.
- [x] Kept category, folder, reminder timestamp, priority, pinning, label color, and AI-provider fallback controls in the editor.
- [x] Added persistent media blocks with editable titles and descriptions. Image blocks show an inline preview, video blocks have embedded playback controls, and audio blocks can be played in the editor.
- [x] Added sheet blocks with editable cells, row/column insertion, selected-column deletion, paste-from-clipboard, and five saved skins.
- [x] Added a data-preserving Room 4→5 migration and kept the existing one-URI attachment field for compatibility. Old attachments open in the new editor and are converted into rich blocks when saved.
- [x] Extended JSON backup import/export to preserve the attachment URI and rich content. Search now includes media titles and descriptions.
- [x] Removed the surrounding header borders from Quick Actions and Quick Access and placed both section controls in the screenshot's arrow-then-Edit order.
- [x] Fixed the folder-picker brace nesting and rich-content state type reported by CI runs #33–34; CI run #35 passed JVM unit tests, Android debug build, and APK upload at commit `b1cf8b8` (artifact: 16,390,845 bytes; expires 2026-11-03).
- [x] This workstation has no Java, Gradle, or Android SDK installed; build and unit test verification ran in GitHub Actions.
- [ ] Check migration, media picking/playback, and the editor layout on a device or emulator; none is available here. Screenshot parity is not claimed.
- [ ] Audio files can be embedded and played; live microphone recording and reminder notification delivery are still not implemented.

## Media viewer and theme correction (2026-10-20; CI passed, device verification pending)
- [x] Image preview and the view control now open an in-app full-screen image viewer rather than relying on an external image app.
- [x] Video cards and their full-screen viewer size themselves from video width/height/rotation metadata, with a safe 16:9 fallback; video cards have the same view control as images.
- [x] Reduced the empty writing area when a note already contains media, so embedded blocks appear near the top of the editor.
- [x] Replaced the sheet's wrapping “Delete column” label with a compact accessible delete icon and made the sheet toolbar horizontally scrollable on narrow screens.
- [x] Applied the selected Light, Dark, or System appearance to the editor, media cards/viewer, audio player, sheet cells, and skin colors.
- [x] Added a unit test for portrait, landscape, rotated, and missing video metadata aspect ratios.
- [x] CI run #37 passed unit tests, Android debug build, and APK upload at commit `a3dc221` (artifact: 16,407,088 bytes; expires 2026-11-03). This workstation has no Java, Gradle, or Android SDK.
- [ ] Verify image opening, portrait/landscape video playback, audio playback, sheet editing, and Light/Dark/System switching on a device or emulator; none is available here, so runtime behavior and screenshot parity are not claimed.

## Sheet row controls (2026-10-20; CI passed, device verification pending)
- [x] Added selectable Row 1, Row 2, and subsequent row chips beside the existing column controls; the selected row is highlighted in the grid.
- [x] Added a compact accessible delete-row icon that removes the selected row and retains at least one row; adding a row selects the new row.
- [x] CI run #39 passed unit tests, Android debug build, and APK upload at commit `52c061c` (artifact: 16,411,380 bytes; expires 2026-11-03). This workstation has no Java, Gradle, or Android SDK.
- [ ] Verify row selection, deletion, and sheet persistence on a device or emulator.

## Image viewer and editing workspace (2026-10-21; run #43 passed, device verification pending)
- [x] Image attachment cards now expose separate Edit and View controls; tapping the preview opens the same full-screen viewer. Video keeps its compact viewer control.
- [x] Image detach asks for confirmation and explicitly leaves the source file on the device untouched.
- [x] The full-screen image viewer supports pinch zoom, bounded pan, and double-tap zoom; the existing video viewer remains separate.
- [x] Added a dedicated editing dialog with rotate, horizontal/vertical flip, centered aspect-ratio crop presets, brightness, contrast, saturation, warmth, six adjustable filters, before/after preview, pen/arrow/text markup, grouped slider undo/redo, and reset.
- [x] Export creates a separate PNG or JPEG in Pictures/ENotes and updates only the selected image block URI when export succeeds. Cancel leaves the original URI and note draft intact.
- [x] Added JVM tests for viewer pan bounds, crop geometry, and edit history; CI run #43 passed all 10 unit tests, the Android debug APK build, and artifact upload.
- [ ] Freeform draggable crop handles, exposure/tint/highlights/shadows/sharpness/blur/vignette, shape/highlighter/sticker tools, movable annotation selection, and editor-canvas zoom/pan are not implemented; the editor currently exposes only the working tools listed above.
- [ ] Android 8/9 gallery export permission handling and EXIF orientation preservation have not been verified.
- [ ] Run the workflow and verify viewer gestures, export/reopen, note draft preservation, and Light/Dark appearance on a device or emulator. No runtime screenshot or device verification has been performed.

## Note formatting, checklists, and photo editor correction (2026-10-21; CI passed, device verification pending)
- [x] Connected the Text toolbar to selection-aware formatting for font size, bold/italic/underline/strike, text color, highlight, alignment, bullets, numbered lists, line spacing, and clearing selected formatting; styles are saved with the note.
- [x] Replaced the static checkbox character with an editable checklist block whose square/circle controls toggle completion and whose tasks can be added, edited, reordered, deleted, or cleared when complete.
- [x] The photo editor now uses the already loaded note image as a preview fallback, decodes the source with Android's image decoder where supported, and can save from the displayed source if a higher-resolution reload fails.
- [x] Kept the photo editor title on one line and added the older Android storage permission needed for gallery export.
- [x] Added unit coverage for text style range editing, numbered-list insertion, and checklist completion.
- [x] CI run #45 found two Compose style parser properties that are internal in the pinned library; replaced them with public unspecified-value checks.
- [x] CI run #46 passed JVM tests, Android debug APK build, and APK artifact upload at commit `75183cc`; artifact size 16,562,169 bytes, expires 2026-11-04.
- [x] This workstation has no Java, Gradle, or Android SDK for a local build; checks ran in GitHub Actions.
- [ ] Verify text selection/formatting, checklist persistence, photo preview/export, and theme colors on a device or emulator; runtime screenshot parity is not claimed.

## V1.1 — organization and documents
- [x] Persisted folders, note assignments, active-note counts, and Room schema migrations are implemented; emulator migration verification remains pending.
- [ ] User-defined tags with Room schema migration and data-preserving tests.
- [ ] Favorites/collections and filterable universal search.
- [ ] Document library using Android's Storage Access Framework.
- [ ] PDF viewing and external document opening; no broad storage permission unless required.
- [x] Multiple image/video/audio/file blocks can be linked to a note; the older single attachment field remains for compatibility. Rich media can be previewed or played from the editor.

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
- [x] Pull-request CI runs JVM tests before the Android debug build; run #43 passed for commit `7b157d3067e171b9e9b6f282bae054e68cb65daa`.
- [ ] Device/emulator smoke tests: launch, create/edit/search, archive/trash/restore, backup export/import, theme.
- [ ] Verify release signing and release APK/AAB separately from debug artifacts.
- [ ] Review privacy policy, data deletion/export, permissions, and store readiness.

## Current honest release status
The repository has a buildable **prototype**, not the complete product. The photo editor preview allocation and decoding correction passed CI run #49, including JVM tests, Android debug APK build, and artifact upload at commit `6556f73`. The artifact is 16,621,077 bytes and expires 2026-11-04. Runtime behavior and screenshot parity remain unchecked until verified on a device. Cloud sync, Google Drive, voice recording, and AI require provider configuration; account and reminder features are not connected.

## Photo editor preview allocation and decoding follow-up (2026-10-21; CI passed, device verification pending)
- [x] Traced the screenshot's error to the preview-render branch: opening with no edits copied the embedded bitmap, then allocated another full ARGB bitmap to display the same pixels. The UI hid the render exception behind “Try a smaller image,” so the screenshot alone does not prove an OOM occurred.
- [x] Removed the unused initial bitmap copy and skip render/allocation when the edit state has no changes. Preview source stays capped at 2,048 px, maintains aspect ratio, and uses a 1,024 px retry after a recoverable memory failure; original URI remains the source for export.
- [x] Made source/render failures cancel safely, release owned temporary bitmaps, report separate actionable messages, and log stage, MIME type, dimensions, and exception details without logging the source URI.
- [x] Unified embedded image and editor decoding, added safe power-of-two sampling for legacy BitmapFactory decoding, and normalize EXIF orientation on Android versions before API 28; ImageDecoder handles newer formats/orientation on API 28+.
- [x] Added JVM tests for preview sizing, extreme dimension sampling, and the no-edit fast path.
- [x] CI run #48 exposed a Compose `produceState` key-call mismatch; corrected the vararg key call in commit `6556f73`.
- [x] CI run #49 passed JVM tests, Android debug APK build, and artifact upload at commit `6556f73`; artifact `enotes-debug-apk` is 16,621,077 bytes and expires 2026-11-04.
- [x] This workstation has no Java, Gradle, Android SDK, or emulator; automated checks ran in GitHub Actions.
- [ ] Check the editor with large and EXIF-rotated photos on a device/emulator; no runtime reproduction or screenshot verification is available here.



## Text formatting and photo markup follow-up (2026-10-21; verification pending)
- [ ] Keep the selected note-text range available while the formatting dialog is open; apply and clear styles against that range, and continue numbered lists when Enter creates a new line.
- [ ] Apply alignment and line spacing to the note text, show the active alignment and current spacing, and retain list/style range offsets when list markers are changed.
- [ ] Add a color palette for pen, translucent highlighter, arrows, and text annotations; render these colors in both the editor preview and saved image.
- [ ] Unit coverage was added for formatting ranges, numbered-list continuation, and colored markup; the test suite and APK build still need to run.
- [ ] Verify the editor interactions and saved image on a device or emulator; no runtime verification is available in this workspace.
