# ENotes

ENotes is an Android notes app built with Kotlin, Jetpack Compose, and Room.

## Download APK

[Download the latest debug APK](downloads/ENotes-debug.apk). It is a test build, not a production-signed release.

## Implemented in the current prototype

- Local-first note creation and editing
- Search note titles and note body text
- Sort active notes by recent update or title
- Pin and archive notes
- Trash, restore, and permanently delete notes with confirmation
- Share individual notes using Android's system share sheet
- Export an importable JSON backup preserving note IDs, content, timestamps, pin/archive status, and trash state
- Import/restore JSON backups through Android's document picker, merging by note ID
- Export a readable plain-text snapshot through the Android share sheet
- GitHub Actions workflow configured to build a debug APK

## Build

The repository includes a GitHub Actions workflow at `.github/workflows/android-apk.yml`. Open the repository's **Actions** tab and inspect the latest **Android APK** run. A successful run uploads an artifact named `enotes-debug-apk`.

The APK is a debug build for testing, not a signed production release.

## Not implemented yet

These features remain roadmap items and should not be considered available in the current prototype:

- Accounts and cloud synchronization
- Google Drive browsing and file modifications
- AI assistant and study tools
- Reminders and calendar integration
- PIN/biometric app lock
- Cloud backup and cross-device restore
- Document viewer/editor
- Advanced tagging, folders, and knowledge map

## Development priorities

1. Verify compilation and fix build errors.
2. Test note create/edit/search/archive/trash/share/export flows on an Android device.
3. Test JSON backup export/import with empty, large, malformed, and duplicate-ID files.
4. Add document management, reminders, app security, and appearance controls.
5. Integrate cloud sync, Drive, and configurable AI providers with explicit user consent.

No API secrets should be embedded in the Android client.


## Living roadmap

See [ROADMAP.md](ROADMAP.md) for verified implementation status, the complete product phases, and release checklist. Items are not marked complete until relevant tests have passed.
