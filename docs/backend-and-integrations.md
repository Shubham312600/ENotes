# ENotes backend and integrations

This document records service boundaries and configuration needs. It does not mean any service is connected.

## Local-only baseline
Core notes should work without sign-in or network. Local data models and persistence must be established before cloud sync. The application should remain useful if optional integrations are not configured.

## Backend responsibilities (if cloud features are enabled)
- Authentication/session validation.
- User-scoped note/document metadata sync.
- Conflict detection and resolution metadata.
- Backup/restore orchestration if implemented remotely.
- AI provider requests, quota enforcement, timeout and rate-limit handling.
- Data export/deletion and account lifecycle where supported.
- Minimal operational logging without note contents, tokens, or unnecessary personal data.

## AI integration contract
Proposed request fields:
- request ID and authenticated user context.
- user prompt.
- optional selected note/document identifiers and content only when the user authorizes it.
- supported conversation context and cancellation/timeout metadata.

Proposed response fields:
- request ID.
- assistant text.
- source references containing actual accessible item IDs and relevant excerpt/location when available.
- structured proposed action, if any, clearly separated from answer text.
- usage/limit metadata and recoverable error code.

Requirements:
- Backend validates user authorization for every referenced item.
- Provider credentials live in server-side secret configuration, never in the Android app.
- Treat model output as untrusted input; validate action schemas server-side and re-check permissions.
- No write action is executed without explicit user confirmation.
- State the AI provider's retention/data policy before sending sensitive content.
- If citations are unavailable, do not manufacture them.

## Google Drive integration
- Register OAuth application and configure consent, redirect/deep link, and approved scopes.
- Use least-privilege access and secure token storage/refresh appropriate to the selected SDK.
- Search/browse results must reflect the authenticated user's actual permissions.
- Re-check file access before opening or modifying.
- Any write operation must present the exact target and proposed change and require user confirmation.
- Handle revocation, expiry, deleted files, quota/rate limits and offline mode.

## Account and sync
Before implementation, decide backend vendor, authentication provider, schema, ownership, encryption, retention and conflict strategy.
- Use stable IDs and version/update metadata.
- Sync queues must be retryable and idempotent where possible.
- A failed request must not be presented as successful.
- Conflicts must preserve both versions until the user resolves them or a documented safe merge succeeds.
- Sign-out behavior must explain what local data remains.
- Account deletion must distinguish local and remote data and report verified outcomes.

## Configuration checklist
- [ ] Backend project and environment.
- [ ] Authentication provider and redirect configuration.
- [ ] Database schema and migration strategy.
- [ ] AI provider and server-side secret configuration.
- [ ] Quota/rate-limit policy and budget alerts.
- [ ] Google OAuth consent, scopes and test accounts.
- [ ] Privacy notice and data-retention policy.
- [ ] Monitoring without sensitive content.
- [ ] Development/staging/production separation.
- [ ] Documented setup instructions and failure recovery.

Until the owner configures required external services and credentials, the app must show a clear not-configured state rather than simulate integration success.
