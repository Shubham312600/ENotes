# ENotes backend setup and deployment

The FastAPI service provides ENotes authentication and the authenticated Gemini, Groq, and GLM proxy. Notes remain local to the Android device. This guide describes the existing Render service `enotes-auth-api` at `https://enotes-auth-api.onrender.com`; it does not instruct you to create another service, database, or Blueprint.

## Existing Render service settings

The known service is connected to `Shubham312600/ENotes`, deploy branch `codex/render-deployment`, root directory `backend`, build command `pip install -r requirements.txt`, start command `alembic upgrade head && uvicorn app.main:app --host 0.0.0.0 --port $PORT --proxy-headers --forwarded-allow-ips='*'`, health path `/health`, and Free plan. The current `render.yaml` declares the same service name and values. Confirm the live dashboard settings before syncing: this repository cannot verify the production service's current environment variables, database, plan, or Blueprint ownership.

Use the existing Render service and its existing Blueprint relationship. Do **not** choose **New → Blueprint** for this change: a new Blueprint is independent and may create a second service. Before an existing Blueprint sync, review the proposed changes. The YAML declares `plan: free`; confirm the existing plan before syncing. It omits the model allowlist environment variables so an existing configured value will not be overwritten; if absent, the application defaults them to empty maps, which block every model in Free-only mode. Review any existing allowlist values for verified eligibility. `generateValue: true` generates a JWT secret only when it does not already exist. Render does not prompt for new `sync: false` secrets when updating an existing Blueprint, so set the encryption key manually in the existing service's environment settings.

The service start command runs database migrations before serving. Before any production deploy, verify the production Alembic revision and take a restorable backup. Do not point these steps at another database or replace `DATABASE_URL` or `JWT_SECRET` as a way to deploy.

## Local setup

Use Python 3.11 or newer from this directory:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
```

The example file is for local development only. Keep `.env` private; it is ignored by Git. Start locally with `python -m uvicorn app.main:app --host 127.0.0.1 --port 8000`. Local development defaults to SQLite and creates its schema automatically. Production requires PostgreSQL and Alembic; it does not auto-create tables.

## Environment variables

Never display, log, commit, or replace existing production secret values. `PORT` is supplied by Render and should not be manually fixed.

| Variable | Required? | Purpose and safe default | Preserve? | Secret? |
| --- | --- | --- | --- | --- |
| `APP_ENV` | Yes in production | Set to `production`; development default is `development`. | Keep production value. | No |
| `DATABASE_URL` | Yes | Persistent PostgreSQL URL. Production accepts `postgresql://` or `postgresql+psycopg://` and requires TLS (`sslmode=require` or stronger). | **Preserve the existing database URL.** | Yes |
| `JWT_SECRET` | Yes | Signs access tokens; production requires at least 32 characters. Render can generate a 256-bit value if absent. | **Preserve it** or existing access tokens become invalid. | Yes |
| `AI_CREDENTIALS_ENCRYPTION_KEY` | Required for user-entered AI credential save/use; not startup | URL-safe Base64 encoding of exactly 32 random bytes. No default. Add it manually as an existing-service secret. Service-managed provider keys do not require it. | **Keep it stable and backed up.** Changing/loss makes old provider keys unreadable. | Yes |
| `GEMINI_API_KEY` | Optional | Service-managed Gemini key. User-entered Gemini credentials take priority when present. | Preserve if configured. | Yes |
| `GROQ_API_KEY` | Optional | Service-managed Groq key. Never substitute `XAI_API_KEY`. User-entered Groq credentials take priority. | Preserve if configured. | Yes |
| `GLM_API_KEY` | Optional | Service-managed Z.AI GLM key. User-entered GLM credentials take priority. | Preserve if configured. | Yes |
| `AI_VERIFIED_FREE_MODELS` | Optional | JSON map of explicitly verified free model IDs; default `{}`. Empty means no model is verified free. | Review before changing. Never infer eligibility. | No |
| `AI_VERIFIED_PAID_MODELS` | Optional | JSON map of explicitly verified paid model IDs; default `{}`. Conflicts with the free map are unknown and blocked in Free-only mode. | Review before changing. | No |
| `DEV_MAIL_SINK` | Optional | Must be `false` in production; default is `true` outside Render. | Keep production false. | No |
| `PUBLIC_APP_URL` | Optional | Password-reset deep link; default `enotes://reset-password`. | Preserve the Android-compatible link. | No |
| `ACCESS_TOKEN_MINUTES` | Optional | Access token lifetime; default `15`. | Preserve if customized. | No |
| `REFRESH_TOKEN_DAYS` | Optional | Refresh session lifetime; default `30`. | Preserve if customized. | No |
| `RESET_TOKEN_MINUTES` | Optional | Password-reset token lifetime; default `30`. | Preserve if customized. | No |
| `RESEND_API_KEY` | Optional | Resend HTTPS email delivery; configure with `RESEND_FROM_EMAIL`. | Preserve if configured. | Yes |
| `RESEND_FROM_EMAIL` | Optional | Verified sender address used with Resend. | Preserve if configured. | No |
| `SMTP_HOST` | Optional | SMTP host; configure with `SMTP_FROM` and, as needed, credentials. | Preserve if configured. | No |
| `SMTP_FROM` | Optional | Sender address; production SMTP delivery requires this and `SMTP_HOST`. | Preserve if configured. | No |
| `SMTP_PORT` | Optional | SMTP port; default `587`. | Preserve if customized. | No |
| `SMTP_USERNAME` | Optional | SMTP authentication username. | Preserve if configured. | Yes |
| `SMTP_PASSWORD` | Optional | SMTP authentication password. | Preserve if configured. | Yes |
| `SMTP_STARTTLS` | Optional | Defaults to `true`; production SMTP rejects `false` when SMTP is configured. | Keep enabled. | No |
| `PORT` | Render-provided | Port passed to Uvicorn by Render. | Do not set a fixed value. | No |

Password-reset delivery needs either the Resend pair or SMTP configuration. Gemini, Groq, and GLM can use either a provider key entered by a signed-in ENotes user (encrypted and scoped to that account) or an optional service-managed Render secret (`GEMINI_API_KEY`, `GROQ_API_KEY`, or `GLM_API_KEY`). A personal key takes priority for that account. Service-managed keys are shared by the backend for accounts without a personal key, are never returned to Android, and must not be placed in source control.

## AI credential encryption and model eligibility

Generate a new key privately in a trusted terminal; this command prints the newly generated value only to that terminal:

```powershell
python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('='))"
```

Set the resulting value privately as Render's `AI_CREDENTIALS_ENCRYPTION_KEY` and store a protected recovery copy. Never paste it into Codex, chat, logs, source control, or the APK. The service uses AES-256-GCM, a cryptographically secure 12-byte nonce, and user/provider authenticated data. The API never returns provider keys. Missing or invalid encryption configuration does not prevent authentication startup; user-entered credential operations fail safely, while a configured service-managed key can still be used. Credential status includes `saved` for the stored row and `credential_usable` for decryptability; `credential_source` identifies `user`, `service`, or `missing` without revealing a key.

Changing or losing this encryption key makes existing ciphertext unreadable unless a key-rotation migration is implemented. Preserve the key across deployments and database restores.

`AI_VERIFIED_FREE_MODELS` and `AI_VERIFIED_PAID_MODELS` are operator-owned JSON objects mapping `gemini`, `groq`, and `glm` to arrays of model IDs. Keep them empty until current pricing and account eligibility are authoritatively checked. Provider model listings alone do not prove a model is free or eligible for a user's account. With a user's Free-only mode on, only IDs exclusively present in the verified-free map are listed and callable; paid, unknown, overlapping, malformed, and unlisted IDs are rejected. An empty verified-free list blocks every model. Do not add guessed IDs.

## Attachments and rate limits

Text attachments are supported for Gemini, Groq, and GLM. Gemini additionally accepts image JPEG/PNG/WebP/GIF and PDF attachments. Groq and GLM text-only chat requests return a structured `422` error for binary attachments; the backend does not silently discard them. AI request bodies are limited to 12 MiB.

Auth and AI rate limits are in-memory and per application process/client IP. They reset when the process restarts and are not shared across multiple instances. Render's public ingress routes through Cloudflare and provides `CF-Connecting-IP`; production limits use that validated single-IP value and ignore caller-controlled `X-Forwarded-For`. If the edge header is missing or malformed, requests share a fail-closed bucket. The existing Uvicorn proxy-header start options remain in place for Render's forwarded HTTPS scheme. Confirm the production request contains the expected Cloudflare client-IP header before relying on per-client limits. No distributed limiter is configured.

## Migrations and database safety

`0001_auth_schema` creates the auth tables. `0002_ai_provider_credentials` additively creates AI credential and preference tables and depends on 0001. A fresh empty PostgreSQL database migrates in revision order; a database recorded at 0001 can upgrade to 0002 without changing existing auth rows. Production migration success has not been established by this document.

If tables already exist but the `alembic_version` record is missing or incorrect, 0001 can fail trying to create existing tables. The upgrades do not drop tables, but the service start will stop before Uvicorn starts. Before deployment, inspect the production `alembic_version`, confirm the schema against the expected migration, and take an independently restorable backup. Do not run `alembic downgrade` as a rollback: downgrade 0002 drops the new AI tables and any stored provider credentials.

For local-only checks, run `python -m alembic upgrade head` against a disposable local database. Never point local commands at the production `DATABASE_URL`.

## Existing-service deployment and verification

1. Review the exact files proposed for the `codex/render-deployment` branch. Do not merge or push until the deployment review is approved.
2. In Render, confirm the existing service name, branch, root directory, build/start command, health path, plan, and existing database/signing secrets. Do not create a service or change the connected branch.
3. Before a service deploy, verify the production migration revision and backup with the database owner. Add `AI_CREDENTIALS_ENCRYPTION_KEY` manually and preserve all current secrets. Review the empty allowlist values and service plan in any Blueprint change preview.
4. Deploy only through the existing service after that review. Render's start command applies `alembic upgrade head` before serving. Monitor startup and migration output without exposing environment values.
5. Verify the public health endpoint:

   ```powershell
   Invoke-RestMethod https://enotes-auth-api.onrender.com/health
   ```

   Expected response: `status: ok`, `service: enotes-auth`. This endpoint does not verify database or AI provider credentials.
6. From a signed-in test account, verify credential status, refresh Gemini and Groq model lists, and test explicitly selected models. For GLM, enter a model ID available to that user's Z.AI account and test it; the current official Z.AI OpenAPI does not document a model-list endpoint, so ENotes does not call `/api/paas/v4/models`. Never claim a provider works based on `/health` alone. Confirm the UI distinguishes `credential_usable` from merely saved configuration.
7. Run registration/login/session refresh/logout and password recovery checks with a dedicated test account. Confirm the existing auth database and user records remain available.

## Rollback considerations

Keep the previous deployment commit and a verified database backup. Rolling application code back to a version that ignores the additive AI tables can preserve those tables; do not downgrade the database or overwrite `DATABASE_URL`. If a migration fails, stop and inspect the exact revision/schema state rather than repeatedly restarting. Restore a backup only into an intentionally selected database after validating the target and plan. Keep the same `JWT_SECRET` and `AI_CREDENTIALS_ENCRYPTION_KEY` for rollback and restore, or sessions and encrypted provider credentials will stop working.

## AI provider gateway

The authenticated AI gateway sends provider requests only from the backend. User-entered provider keys are encrypted in PostgreSQL. Service-managed provider keys can instead be loaded from the optional Render secrets `GEMINI_API_KEY`, `GROQ_API_KEY`, and `GLM_API_KEY`; each key is used only for its matching provider. To accept user-entered keys, create a 32-byte random encryption key and set it as the Render secret `AI_CREDENTIALS_ENCRYPTION_KEY`. Service-managed-key requests do not require the credential-encryption key. For local PowerShell development, generate a suitable base64url value with:

```powershell
python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('='))"
```

Keep this value in the host's secret manager and make a protected recovery copy. Losing or rotating it without re-encrypting stored credentials makes those provider keys unreadable; users would need to replace them. The migration `0002_ai_provider_credentials` is applied by the existing Render start command.

`AI_VERIFIED_FREE_MODELS` and `AI_VERIFIED_PAID_MODELS` are server-owned JSON objects mapping provider IDs to model IDs whose pricing and account eligibility have been independently verified. Both default to empty. The provider model-list APIs do not establish each user's billing tier, so models not verified in these lists are marked unverified and blocked whenever a user enables Free-only mode. If an ID appears in both lists, it remains unverified. Do not add IDs without checking current pricing and account eligibility.

The AI endpoints require the existing ENotes Bearer access token:

- `PUT/GET/DELETE /api/v1/ai/{glm|gemini|groq}/credential`
- `GET /api/v1/ai/{provider}/models`
- `POST /api/v1/ai/{provider}/test`
- `POST /api/v1/ai/{provider}/chat`
- `GET /api/v1/ai/settings` and `PUT /api/v1/ai/settings/free-only`

Credentials are never returned by the gateway. A successful Android build does not mean these routes are live: deploy this backend migration and encryption secret, then run the authenticated provider tests from a signed-in device before treating a provider as connected.

Render’s Free web service uses an ephemeral filesystem, so SQLite must not be used there. The production settings reject SQLite and require PostgreSQL. The repository’s SQLite URL remains for local development and isolated tests only.

## Password-reset email

Signup and login work without an email provider; email verification is not claimed. Password recovery is deliberately disabled in production until an email provider is configured, and returns a clear `503` instead of implying that a message was sent.

Render Free blocks outbound SMTP on ports 25, 465, and 587. For that host, use an email provider’s HTTPS API on port 443. The backend supports Resend with two Render secrets:

- `RESEND_API_KEY`: create a sending API key in Resend.
- `RESEND_FROM_EMAIL`: an address on a domain verified in that Resend account, for example `ENotes <no-reply@your-verified-domain>`.

Add these under the Render service’s environment settings and redeploy. A provider account is free to start, but sending to users requires a verified sender domain; domain registration may cost money. Resend currently lists 3,000 emails/month and 100/day on its Free plan. If you do not configure a provider, forgot-password requests remain unavailable rather than returning a success message.

SMTP settings remain supported for hosts that allow SMTP egress. Never put API keys, SMTP credentials, or database URLs in this file.

## Build a phone APK

Do not package a phone APK until step 6 has produced a verified public URL. From the Android project root, pass that exact HTTPS origin as a Gradle property:

```powershell
gradle :app:assembleDebug -PENOTES_API_BASE_URL=https://<actual-render-hostname>
```

For a release APK, use the same property with `:app:assembleRelease`. APK packaging fails if the property is missing, non-HTTPS, an IP address, or a local/LAN hostname. The app also rejects non-HTTPS and local/LAN addresses at runtime. Do not use a guessed hostname or the development computer’s address.

After building, inspect `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release.apk`, install it on the phone, and repeat signup, login, session restore, logout, and password reset first on Wi-Fi and then on mobile data. The backend URL must remain the same in both cases.

## Free-tier limits and recovery

This is a $0 starting configuration, not a production uptime promise. Render says Free web services sleep after 15 minutes without traffic and can take about a minute to wake; it also says Free instances are intended for previews/hobby use rather than production. Neon Free currently includes finite monthly compute and storage allowances, and its database can scale to zero. First requests can be slower, and service limits can change. Upgrade or move providers if ENotes needs dependable always-on sign-in or operational guarantees.

Neon’s short instant-restore window is not a substitute for an independent backup. Periodically export the production database from a trusted machine with PostgreSQL client tools, while keeping the connection string out of shell history where possible:

```powershell
pg_dump --format=custom --no-owner --no-privileges --dbname=$env:DATABASE_URL --file=".\enotes-auth-$(Get-Date -Format yyyyMMdd-HHmmss).dump"
```

Store the dump encrypted with restricted access. Test restores into a separate empty Neon project; do not run a destructive restore over the live account database. For a restore:

```powershell
pg_restore --no-owner --no-privileges --dbname=$env:RESTORE_DATABASE_URL .\enotes-auth-backup.dump
```

Account records, password hashes, reset tokens, and refresh sessions are remote. Notes remain in the phone’s local Room database and are not synced or partitioned by ENotes account in this implementation.

## Deployment boundary

No cloud service is deployed by this repository change. Creating the Neon project, entering its private connection string in Render, and linking the GitHub repository require the account owner’s dashboard actions. Do not call the backend production-ready until the assigned HTTPS URL, public health response, and phone sign-in over mobile data have all been verified.
