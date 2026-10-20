# ENotes — Innovation and Multitasking Specification

Status: planned product scope. Features become "implemented" only after code and tests verify them. Prioritize reliability and user trust over feature count.

## Product differentiators

### 1. Command Center / Universal Quick Capture (P0)
- Create note, checklist, reminder, or quick capture from one persistent action.
- Capture text shared from other apps through Android Sharesheet.
- Optional home-screen widget and app shortcuts for New Note, Search, and New Reminder.
- Preserve drafts through process death and offline use.
- Route captured content into an explicitly selected destination; never upload silently.

### 2. Universal Search (P0/P1)
- Search note titles and body, tags, folders, and imported document text only where extraction is supported.
- Filters: content type, folder/tag, date, local/cloud source, pinned/archived.
- Recent searches, highlighted matches, snippets, and keyboard navigation.
- Explain unavailable sources when Drive is disconnected or a document is not indexed.
- Keep indexing local by default; cloud search only over explicitly authorized content.

### 3. Split-workspace multitasking (P1)
- Multi-pane layout on tablets/foldables: list + editor + optional detail pane.
- On phones, preserve navigation stack and draft state while switching tasks.
- Recent-workspace switcher to move between open notes/documents without losing edits.
- Drag-and-drop between supported panes on large screens; never make drag-and-drop the only way to act.
- Multi-select and batch operations with action-specific validation and confirmation.

### 4. Living Knowledge Map (P2)
- Visual graph of notes, tags, folders, and documents based only on explainable relationships: shared tags, explicit links, and verified content references.
- Tap a node to open its source; show why two items are connected.
- Filter by folder, tag, time, or source; support a useful list alternative.
- Build locally where practical. AI-suggested links must be labeled as suggestions and accepted by the user.
- Do not claim semantic links are facts; let users dismiss incorrect suggestions.

### 5. AI Study and Knowledge Assistant (P1/P2)
- Summarize selected notes/documents, explain concepts, create study questions, turn selected text into flashcards, and compare documents.
- "Ask my notes" answers must cite actual accessible source items and distinguish direct evidence from model-generated inference.
- Study mode: flashcards, self-test, spaced review schedule, progress indicators and user-controlled reminders.
- AI-generated material is a draft; users can review/edit before saving.
- Provide offline fallback to local search and notes when AI is unavailable.
- Show data-sharing notice before sending content to an external provider.

### 6. Actionable AI with a safety boundary (P1)
- Suggest reminders, checklists, tags, summaries, and links from selected content.
- Present exact proposed action, target, date/time, and affected data before execution.
- Explicit user confirmation for every create/update/delete, Drive write, or bulk operation.
- Cancel must leave state unchanged; execution results must be verified before success is shown.
- Do not let model output directly execute arbitrary code or unrestricted tools.

### 7. Knowledge inbox and workflows (P1)
- Inbox for shared text, imported documents, unprocessed captures, and user-created follow-ups.
- Convert inbox item to note, task/checklist, reminder, or archive.
- Optional user-configured rules (e.g. default folder for shared links); rules must be visible and reversible.
- No background access to other apps' content without a user action.

### 8. Documents as first-class content (P1)
- Import through Android system picker; attach files to notes; track source and availability.
- Document hub with recent, pinned, local/remote, type and indexing filters.
- Viewer capability is format-specific and honest; external handoff when unsupported.
- OCR/text extraction is optional and only shown for implemented formats; clearly indicate processing and local/cloud processing.
- Never assume a remote Drive file is downloaded or available offline.

### 9. Focus and planning tools (P1)
- Reminder calendar and agenda, recurring reminders, snooze/complete actions.
- Optional focus session linked to a note/task, with user-controlled duration and no punitive streak mechanics.
- Daily review: pinned items, due reminders, recent notes and inbox count; dismissible and configurable.
- Smart reminder proposals require preview and confirmation.

### 10. Safe sync and continuity (P1)
- Local-first write path; visible sync queue and last-success timestamp.
- Stable IDs, retryable/idempotent sync, conflict review preserving both versions.
- Exportable backup, integrity validation, restore preview, explicit merge/overwrite decision.
- Explain sign-out effects and distinguish device-local data from account data.
- Avoid promising end-to-end encryption unless it is genuinely designed, implemented and audited.

### 11. Personalization and accessibility (P0/P1)
- Light/dark/system themes, dynamic color where supported, font scaling, reduced motion, compact/comfortable density.
- List/grid preference, editor defaults, optional accent palette, haptics and notification preferences.
- Screen-reader labels, logical focus, sufficient contrast, non-color status indicators, keyboard support and accessible alternatives to gestures.
- Respect Android system settings and avoid decorative animations that delay work.

### 12. Power-user productivity (P2)
- Configurable keyboard shortcuts on hardware-keyboard devices.
- Markdown/plain-text import/export and stable portable note export.
- Optional note linking using explicit syntax and backlinks.
- Version history or snapshots before risky bulk edits if storage design supports it.
- Custom templates and reusable checklists.
- Batch tagging/moving/archive/export, with preview for destructive actions.

## Navigation model
Primary destinations: Home, Notes, Documents, Search, Reminders, AI, and Settings. Do not force all destinations into a crowded bottom bar; use a small primary navigation set and a consistent navigation drawer/overflow for secondary destinations. Keep a global search and quick-capture entry point easy to reach.

## Priority rules
- P0: reliability, local persistence, editor, search, navigation, accessibility, error handling.
- P1: organization, attachments/documents, reminders, import/share capture, backup/export, real account/sync, Drive and AI after configuration.
- P2: knowledge graph, advanced multi-pane interactions, advanced study tools and power-user workflows.
- Do not delay a reliable notes foundation to build flashy AI or graph demos.

## Explicit non-goals / trust boundaries
- No hidden uploads, surprise subscriptions, or fabricated "free forever" claims.
- No silent deletion, overwrite, external write, or AI action.
- No false source citations, fake connection states, or fake progress.
- No shipping provider secrets in the mobile binary.
- No permission request without a user-facing reason.
- No unnecessary contact/location/microphone/storage access.
- No dark patterns, manipulative streaks, or confusing destructive controls.

## Success measures
- Core note operations work offline and survive restarts.
- Users can find a known note quickly with search and filters.
- Switching tasks never silently loses a draft.
- Every remote action exposes its source/account and recoverable failure path.
- Critical flows pass tests at large font scale and with screen readers.
- All claimed features map to code, tests, and documented configuration.
