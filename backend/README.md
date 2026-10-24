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

The backend creates its local development schema on startup. To use Alembic migrations instead, run `python -m alembic upgrade head` from this directory. For production, set `APP_ENV=production`, configure a strong random `JWT_SECRET` (at least 32 characters), and provide a TLS-enabled PostgreSQL `DATABASE_URL`. Production startup deliberately rejects SQLite and does not create its schema automatically; run `alembic upgrade head` before serving. Password-reset mail is optional, but recovery returns `503` until SMTP or Resend is configured. See [DEPLOYMENT.md](DEPLOYMENT.md) for the Render and Neon setup.

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

For real delivery, configure either SMTP (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_STARTTLS=true`) or Resend (`RESEND_API_KEY`, `RESEND_FROM_EMAIL`), and set `PUBLIC_APP_URL=enotes://reset-password`. The Android app handles that reset deep link. The forgot-password response stays generic whether an email exists or not. Email verification is not enabled in this build; no account is described as verified by the client. Google and Apple sign-in buttons remain visible in the supplied design but report that provider credentials and server-side token validation are not configured. ENotes authentication does not grant Google Drive access.

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

## Database and operations

The initial Alembic revision creates only account, session, and reset-token tables. SQLite is for local development; PostgreSQL is supported via `DATABASE_URL=postgresql+psycopg://...`. Back up the configured database using the database's standard, access-controlled backup procedure; for local SQLite, stop the service and copy `data/enotes.db` and retain the copy securely. Test restore procedures before relying on a backup. Account deletion cascades to account sessions and reset tokens, but never deletes local notes from a device.

Production deployment still needs an internet-reachable HTTPS host, PostgreSQL credentials, a strong JWT secret, operational backups, and privacy/retention policy. Configure a password-reset mail provider if you want account recovery. Free hosting/email quotas vary and are not guaranteed. This repository does not contain deployment credentials or claim a public production service is running.
