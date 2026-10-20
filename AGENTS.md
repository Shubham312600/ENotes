# ENotes — Agent Instructions

## Before changing code
- Read this file and the relevant documents in `docs/`.
- Inspect the current repository and preserve existing work; do not assume the app framework or architecture.
- If the repository has no app scaffold, propose a minimal Android-first architecture before adding framework-specific files.
- State assumptions explicitly. Never claim a feature is implemented, tested, or connected unless verified.

## Product direction
ENotes is an Android-first notes and document workspace. The planned product includes notes and folders, document viewing/import, optional Google Drive integration, reminders, search, account/sync, privacy controls, themes, app lock, and an AI assistant. See `docs/screen-inventory.md`.

## Engineering principles
- Prefer small, coherent changes and reusable components over one giant implementation.
- Keep core note-taking useful offline; clearly mark features that require network, account, provider, or permissions.
- Use least-privilege Android permissions and platform pickers where possible.
- Never hard-code API secrets or put provider credentials in the client. Use a secure backend for AI/provider calls.
- Treat AI output as a proposal for consequential actions. Show exact details and require explicit confirmation before modifying Drive, creating reminders, deleting data, or overwriting/restoring content.
- Do not silently overwrite conflicting note content. Preserve drafts and make conflicts recoverable.
- Handle loading, empty, offline, permission-denied, and error states intentionally.
- Provide accessible labels, touch targets, font scaling, and light/dark theme support.
- Add or update tests for behavior changed. Run available checks and report their actual results; if checks cannot run, say so.

## Delivery workflow
1. Inspect repository, framework, build instructions, and current tests.
2. Write a short plan before substantial implementation.
3. Implement one vertical slice at a time.
4. Run the checks available in the environment.
5. Summarize files changed, behavior added, checks run, known gaps, and next steps.
