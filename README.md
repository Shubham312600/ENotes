# ENotes

ENotes is an Android-first notes and document workspace designed around reliable offline note-taking, fast capture, powerful organization, reminders, documents, and optional AI/Drive integrations.

**Current status:** An initial native Android starter has been committed. It includes a Compose notes list/editor and a Room-backed local note store. The app has not yet been built or tested in an Android SDK environment, so treat buildability as unverified until CI or Android Studio confirms it.

## Project roadmap
Read [docs/README.md](docs/README.md) for the full documentation index. Start with:
- [Complete product and engineering roadmap](docs/complete-product-roadmap.md)
- [80-screen inventory](docs/screen-inventory.md)
- [Innovation and multitasking specification](docs/innovation-and-multitasking.md)
- [Implementation plan](docs/implementation-plan.md)
- [Architecture decisions](docs/architecture-decisions.md)
- [Testing and release checklist](docs/testing-and-release-checklist.md)

## Current starter functionality
- Native Android app using Kotlin and Jetpack Compose.
- Local Room database for notes.
- Create/edit notes, local title/body search, pin/unpin and move-to-trash.
- Core note flow designed to work without account or network access.

## Planned product priorities
1. Verify a clean build and add tests for persistence and core interactions.
2. Expand note organization: folders, tags, archive/trash recovery, templates, autosave and drafts.
3. Add document import/viewer handoff, reminders, share capture, accessibility and export/backup.
4. Integrate account/sync, Google Drive and AI only with real configuration, transparent privacy behavior, and secure authorization.
5. Add advanced multitasking such as adaptive multi-pane workspaces, a Living Knowledge Map, AI study tools, and safe action proposals.

## Trust principles
- Never claim a planned feature works until it is implemented and tested.
- Never silently delete or overwrite data.
- Never upload note content without a clear user action and appropriate notice.
- Never put private service keys in the Android app.
- Core notes should remain useful offline.

## Build
Open this repository in a compatible Android Studio installation with JDK 17 and Android SDK platform 35 installed, then allow Gradle sync. The first build must be verified before adding more layers. A Gradle wrapper and CI build should be added as the next reliability step.
