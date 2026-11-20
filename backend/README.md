# ENotes account backend

This FastAPI service provides ENotes account registration, sign-in, revocable sessions, password reset/change, and account deletion. It is a separate optional service: Room notes continue to work offline and are not uploaded or gated by account sign-in.

## Local development

Python 3.11 or newer is recommended. From this directory, create an environment and install the pinned, open-source dependencies:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
```

Start the API (SQLite is created at `backend/data/enotes.db`):

```powershell
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

The backend creates its local development schema on startup. To use Alembic migrations instead, run `python -m alembic upgrade head` from this directory. For a release deployment set `APP_ENV=production`, configure a strong random `JWT_SECRET` (at least 32 characters), `DATABASE_URL`, and SMTP settings before starting; startup deliberately does not create a production schema automatically.

Run the automated API and security-flow tests:

```powershell
python -m pytest -q
```

## Android connection

- Phone and release APK packaging require an explicit public HTTPS API origin through `-PENOTES_API_BASE_URL`. There is no emulator, localhost, or LAN fallback. APK packaging rejects missing, non-HTTPS, IP-literal, and local/LAN hostnames; the Android client repeats the check at runtime.
- Keep the same hosted API origin when the phone switches between Wi-Fi and mobile data. If a URL is not configured, account actions show a clear setup error and local notes remain available.
- See [DEPLOYMENT.md](DEPLOYMENT.md) for Neon, Render, email, public-health verification, and phone build steps.

## Password reset mail

The development mail sink is enabled by default. It stores reset messages in memory and is available only outside production at `GET /api/v1/dev/mailbox?email=<address>`. The returned deep link is for local development and contains a short-lived, single-use reset token; do not expose this endpoint publicly. Tokens are stored in the database only as SHA-256 hashes, and are never included in production API responses or logs.

For real delivery configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS=true`, and `PUBLIC_APP_URL=enotes://reset-password`. The Android app handles that reset deep link. The forgot-password response stays generic whether an email exists or not. Email verification is not enabled in this build; no account is described as verified by the client. Google and Apple sign-in buttons remain visible in the supplied design but report that provider credentials and server-side token validation are not configured. ENotes authentication does not grant Google Drive access.

## API

All JSON routes are versioned under `/api/v1/auth`:

| Method and path | Purpose |
| --- | --- |
| `POST /register` | Create a unique account and start a session |
| `POST /login` | Validate credentials and start a session |
| `POST /refresh` | Rotate a refresh token and issue a short-lived access token |
| `POST /logout` | Revoke the supplied refresh-token session |
| `GET /me` | Return the authenticated account profile |
| `POST /password/forgot` | Send generic password-reset instructions |
| `POST /password/reset` | Consume a reset token once and update the password |
| `POST /password/change` | Reauthenticate, change password, and revoke all sessions |
| `DELETE /me` | Reauthenticate and delete the ENotes account |

Successful registration/login/refresh returns `access_token`, `refresh_token`, `token_type`, `expires_in`, and a safe `user` object. Errors use `{"error":{"code":"...","message":"..."}}`; validation errors also include field names. Passwords use Argon2id. Access tokens are signed JWTs tied to a live server-side session; refresh tokens are opaque, rotated, revocable values stored only as hashes. The Android app stores persistent tokens encrypted with an Android Keystore AES-GCM key.

Authentication throttling is per process and client IP. A multi-instance deployment must put a shared limiter (for example Redis-backed rate limiting at a reverse proxy) in front of the API. Serve production traffic behind HTTPS; do not disable certificate verification.

## AI provider gateway

The optional authenticated AI gateway supports Gemini, Groq, and GLM through the FastAPI backend. Users can enter provider keys in ENotes; the backend encrypts them with AES-256-GCM before saving them to PostgreSQL. Keys are scoped to the signed-in user and never returned by the API. Alternatively, the service can use optional provider-specific secrets (`GEMINI_API_KEY`, `GROQ_API_KEY`, `GLM_API_KEY`) for accounts without a personal key; each secret is isolated to its provider and never returned to Android. Configure `AI_CREDENTIALS_ENCRYPTION_KEY` privately on the backend to enable user-entered credentials, and preserve it across restarts and restores. Missing encryption configuration does not disable service-managed provider keys or account authentication.

Free-only mode is off by default. When enabled, the backend allows only model IDs explicitly listed in `AI_VERIFIED_FREE_MODELS`; paid, unknown, overlapping, malformed, and unlisted IDs are blocked. An empty free-model list means no model can be used in Free-only mode. Do not populate the allowlist without checking current provider pricing and account eligibility. Provider model listings alone do not prove free eligibility.

Gemini and Groq model IDs are discovered from their documented model-list APIs. The current Z.AI OpenAPI documents chat completions but no model-list API, so GLM IDs must be entered from the user's Z.AI account and tested through chat; the backend does not call an undocumented `/models` route.

Gemini accepts text, JPEG/PNG/WebP/GIF images, and PDF attachments. Groq and GLM accept text attachments only and return a structured client error for binary attachments. Authentication and AI rate limits are in-memory per process; they reset after restart and are not shared between multiple instances.

See [DEPLOYMENT.md](DEPLOYMENT.md) for the complete environment-variable table, key generation and backup, PostgreSQL migrations, existing Render service deployment review, verification, and rollback guidance.

## Database and operations

Alembic revision `0001_auth_schema` creates account, session, and reset-token tables. Revision `0002_ai_provider_credentials` adds the AI credentials and preferences. SQLite is for local development; production PostgreSQL uses the installed `psycopg` driver and requires TLS. Back up the configured database using the database's standard, access-controlled backup procedure; for local SQLite, stop the service and copy `data/enotes.db` and retain the copy securely. Test restore procedures before relying on a backup. Account deletion cascades to account sessions, reset tokens, and AI credentials, but never deletes local notes from a device.

Production deployment still needs an internet-reachable host, valid HTTPS, database credentials, a strong JWT secret, SMTP delivery, operational backups, and privacy/retention policy. Free hosting/email quotas vary and are not guaranteed. This repository does not contain deployment credentials or claim a public production service is running.
