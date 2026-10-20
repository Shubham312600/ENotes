# ENotes testing and release checklist

## Repository and build
- [ ] Fresh checkout builds using documented steps.
- [ ] Debug and release variants compile.
- [ ] Static analysis/lint passes or documented findings are triaged.
- [ ] Dependency versions are reproducible.
- [ ] No API keys, OAuth secrets, tokens, or private user content are committed.

## Core notes
- [ ] Create, edit, read, delete-to-trash, restore, and permanent delete.
- [ ] Data persists after process/app restart.
- [ ] Autosave and unsaved-draft recovery.
- [ ] Search, sort, pin, folders, tags, archive, templates, bulk actions.
- [ ] Conflicts never silently discard content.

## Documents
- [ ] System picker and cancel path.
- [ ] Supported import success and duplicate handling.
- [ ] Unsupported/corrupt file handling.
- [ ] Preview and external viewer handoff.
- [ ] Storage cleanup explains what is removed.

## Reminders
- [ ] Date/time validation and recurrence.
- [ ] Notification permission granted/denied/revoked.
- [ ] Notification opens correct reminder.
- [ ] Timezone and daylight-saving behavior considered where relevant.

## Account, sync and backup
- [ ] Sign-in and sign-out failure cases.
- [ ] Offline queue, retry and idempotency.
- [ ] Sync conflict preserves both versions.
- [ ] Backup integrity check.
- [ ] Restore preview, confirmation and recovery path.
- [ ] Account/data deletion distinguishes local and cloud data.

## Google Drive
- [ ] OAuth consent and return path.
- [ ] Revoked/expired access and reconnect.
- [ ] Search/browse respects real user permissions.
- [ ] Missing/deleted file and offline errors.
- [ ] Every write action requires exact confirmation.

## AI
- [ ] Provider configured state versus not-configured state.
- [ ] Timeout, rate limit, quota exhaustion and provider outage.
- [ ] Draft preservation and cancellation behavior.
- [ ] Source references point to real accessible content.
- [ ] Action proposals cannot execute before explicit confirmation.
- [ ] Secrets remain server-side.

## Accessibility and UI
- [ ] Light, dark and system appearance.
- [ ] Large font and display scaling.
- [ ] Screen reader labels/focus order.
- [ ] Adequate touch targets and contrast.
- [ ] Loading, empty, offline, error and permission-denied states.
- [ ] Small and large screen sizes.

## Release
- [ ] App identity, icon, versioning and release notes.
- [ ] Privacy notice matches actual data flows.
- [ ] Permissions reviewed and justified.
- [ ] Crash reporting/logging reviewed for sensitive data.
- [ ] Release build and smoke test verified.
- [ ] Known limitations and setup requirements documented.
- [ ] Recovery/rollback and data migration plan reviewed.
