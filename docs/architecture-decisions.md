# ENotes Architecture Decision Record

This file records the proposed baseline. Verify current toolchain versions and compatibility before implementation; update this file when a decision changes.

## ADR-001 — Android-first native app
**Proposal:** Kotlin + Jetpack Compose, a single Android application module initially, Material 3 design components, unidirectional UI state, and repository boundaries.

**Why:** Native Android permission, document-picker, notification, biometric, widget, and share-sheet integrations are central to the roadmap. Compose supports reusable and adaptive UI.

**Constraints:** Keep domain/data layers testable without Android UI. Avoid premature multi-module complexity; split modules only when it improves boundaries or build time.

## ADR-002 — Local-first data
**Proposal:** A local relational database with schema migrations as the authoritative offline store; repository APIs isolate persistence from screens.

**Requirements:** Stable IDs, timestamps, explicit deletion/trash state, version fields, migration tests, and rebuildable search index. Select a maintained database library after checking current Android compatibility.

## ADR-003 — UI architecture
- Screens render immutable UI state and emit user intents.
- ViewModels or equivalent presentation controllers own screen state.
- Reusable components for app bars, search, note cards, empty/error/loading states and confirmations.
- Navigation destinations must have stable IDs matching the screen inventory.
- No business logic or network calls directly inside composable UI functions.

## ADR-004 — Search and indexing
- Start with reliable local title/body search.
- Add indexed document text only for formats the app can safely extract.
- Keep search provider/source visible and handle partial results.
- If semantic search is added, expose why results matched and retain keyword search as a dependable fallback.

## ADR-005 — Backend is optional for core use
Core note-taking must not require an account or remote service. Sync and AI should be modular capabilities with clear disconnected/not-configured states. Select backend and provider only after cost, privacy, retention, authentication and quota tradeoffs are documented.

## ADR-006 — Secrets and authorization
- No private provider keys or client secrets in source, resources, or app binary.
- Enforce user authorization on the backend for every resource.
- Use platform/provider-supported OAuth and secure token storage.
- Treat AI output and imported files as untrusted data.
- Confirm writes and destructive operations with the user.

## ADR-007 — Testing strategy
- Unit tests for domain logic, recurrence rules, conflict policy and validation.
- Persistence tests for migrations, restart durability, trash/restore and search.
- UI tests for core navigation, editing, confirmation and accessibility-sensitive flows.
- Integration tests for configured external services using safe test accounts.
- CI builds from a clean checkout; never mark a phase complete based on source inspection alone.

## Decision status
These are proposals, not proof that the Android project has been scaffolded. The first implementation task is to verify available Android/Gradle tooling, choose compatible versions, create a buildable skeleton, and record actual verification results.
