from __future__ import annotations

import smtplib
import json
import time
from contextlib import asynccontextmanager
from email.message import EmailMessage
from urllib.parse import urlencode
from urllib.request import Request as UrlRequest, urlopen

import jwt
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .config import Settings
from .database import Base, build_engine, build_session_factory
from .models import AIProviderCredential, AIUserPreference, AuthSession, PasswordResetToken, User, new_id
from .rate_limit import RateLimiter, limit_request
from .schemas import (
    Credentials,
    DeleteAccountRequest,
    EmailRequest,
    LogoutRequest,
    MessageResponse,
    PasswordChangeRequest,
    PasswordResetRequest,
    RefreshRequest,
    RegisterRequest,
    AIChatProxyRequest,
    AIFreeOnlyRequest,
    AIProviderCredentialRequest,
    SessionResponse,
    UserResponse,
)
from .security import decode_access_token, hash_password, hash_secret, new_opaque_token, issue_access_token, verify_password
from .ai_security import CredentialVaultUnavailable, decrypt_provider_key, encrypt_provider_key
from .ai_proxy import PROVIDERS, ProviderApiError, chat_provider, list_provider_models, test_provider_model


def failure(status: int, code: str, message: str) -> None:
    raise HTTPException(status_code=status, detail={"code": code, "message": message})


def create_app(settings: Settings | None = None) -> FastAPI:
    config = settings or Settings.from_environment()
    engine = build_engine(config.database_url)
    session_factory = build_session_factory(engine)

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        if config.auto_create_schema:
            Base.metadata.create_all(engine)
        yield
        engine.dispose()

    app = FastAPI(title="ENotes Authentication API", version="1.0.0", lifespan=lifespan)
    app.state.settings = config
    app.state.engine = engine
    app.state.session_factory = session_factory
    app.state.rate_limiter = RateLimiter()
    app.state.mail_sink: list[dict[str, object]] = []
    bearer = HTTPBearer(auto_error=False)

    @app.middleware("http")
    async def enforce_auth_body_size(request: Request, call_next):
        if request.url.path.startswith("/api/v1/auth/") or request.url.path.startswith("/api/v1/ai/"):
            content_length = request.headers.get("content-length", "")
            max_body = 12 * 1024 * 1024 if request.url.path.startswith("/api/v1/ai/") else 16_384
            if content_length.isdigit() and int(content_length) > max_body:
                return JSONResponse(status_code=413, content={"error": {"code": "request_too_large", "message": "Request body is too large."}})
        return await call_next(request)

    def get_db(request: Request):
        db = request.app.state.session_factory()
        try:
            yield db
        finally:
            db.close()

    def current_user(
        request: Request,
        credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
        db: Session = Depends(get_db),
    ) -> User:
        if credentials is None or credentials.scheme.lower() != "bearer":
            failure(401, "missing_access_token", "Sign in to continue.")
        try:
            claims = decode_access_token(config, credentials.credentials)
            user_id, session_id = str(claims["sub"]), str(claims["sid"])
        except (jwt.PyJWTError, KeyError, TypeError, ValueError):
            failure(401, "invalid_access_token", "Your session has expired. Sign in again.")
        auth_session = db.get(AuthSession, session_id)
        user = db.get(User, user_id)
        if auth_session is None or auth_session.user_id != user_id or auth_session.revoked_at is not None or auth_session.expires_at <= int(time.time()) or user is None or not user.is_active:
            failure(401, "inactive_session", "Your session has expired. Sign in again.")
        return user

    def checked_provider(provider: str) -> str:
        value = provider.strip().lower()
        if value not in PROVIDERS:
            failure(404, "provider_not_supported", "This AI provider is not supported by the secure gateway.")
        return value

    def provider_credential(db: Session, user: User, provider: str) -> tuple[AIProviderCredential | None, str]:
        row = db.scalar(select(AIProviderCredential).where(
            AIProviderCredential.user_id == user.id,
            AIProviderCredential.provider_id == provider,
        ))
        if row is not None:
            try:
                return row, decrypt_provider_key(config, user.id, provider, row.encrypted_key)
            except CredentialVaultUnavailable:
                # A service-managed key can keep the provider usable if a user's
                # separately stored credential cannot be decrypted.
                service_key = config.provider_api_key(provider)
                if service_key:
                    return row, service_key
                failure(503, "credential_vault_unavailable", "Saved provider credentials cannot be opened. Re-enter the provider key in ENotes AI settings.")
        service_key = config.provider_api_key(provider)
        if service_key:
            return None, service_key
        failure(412, "missing_provider_key", f"Add a {provider.upper()} API key in ENotes AI settings or configure the service key.")

    def provider_error_response(error: ProviderApiError) -> None:
        raise HTTPException(status_code=error.status or 502, detail={
            "code": error.code,
            "provider": error.provider,
            "message": error.message,
            "technical_details": error.technical_details,
        })

    def free_only_for(db: Session, user: User) -> bool:
        preference = db.get(AIUserPreference, user.id)
        return bool(preference and preference.free_only)

    def ai_transport(request: Request):
        return getattr(request.app.state, "ai_http_transport", None)

    @app.exception_handler(HTTPException)
    async def http_error_handler(_request: Request, exc: HTTPException):
        detail = exc.detail
        if isinstance(detail, dict):
            error = detail
        else:
            error = {"code": "request_failed", "message": str(detail)}
        return JSONResponse(status_code=exc.status_code, content={"error": error}, headers=exc.headers)

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(_request: Request, exc: RequestValidationError):
        fields = [{"field": ".".join(str(part) for part in error["loc"] if part != "body"), "message": error["msg"]} for error in exc.errors()]
        return JSONResponse(status_code=422, content={"error": {"code": "invalid_request", "message": "Check the submitted information.", "fields": fields}})

    def create_session(db: Session, user: User, family_id: str | None = None) -> SessionResponse:
        now = int(time.time())
        raw_refresh = new_opaque_token()
        session_id = new_id()
        row = AuthSession(
            id=session_id,
            family_id=family_id or new_id(),
            user_id=user.id,
            token_hash=hash_secret(raw_refresh),
            created_at=now,
            expires_at=now + config.refresh_token_days * 86_400,
        )
        db.add(row)
        db.flush()
        access = issue_access_token(config, user.id, session_id, now)
        db.commit()
        return SessionResponse(access_token=access, refresh_token=raw_refresh, expires_in=config.access_token_minutes * 60, user=UserResponse.model_validate(user))

    def revoke_family(db: Session, family_id: str, now: int) -> None:
        db.execute(update(AuthSession).where(AuthSession.family_id == family_id, AuthSession.revoked_at.is_(None)).values(revoked_at=now))
        db.commit()

    def reset_url(token: str, email: str) -> str:
        separator = "&" if "?" in config.public_app_url else "?"
        return f"{config.public_app_url}{separator}{urlencode({'token': token, 'email': email})}"

    def send_reset_email(user: User, token: str, request: Request) -> None:
        url = reset_url(token, user.email)
        text = f"Use this link to reset your ENotes password. It expires in {config.reset_token_minutes} minutes and can only be used once.\n\n{url}\n"
        if config.resend_configured:
            http_request = UrlRequest(
                "https://api.resend.com/emails",
                data=json.dumps({
                    "from": config.resend_from_email,
                    "to": [user.email],
                    "subject": "Reset your ENotes password",
                    "text": text,
                }).encode("utf-8"),
                headers={
                    "Authorization": f"Bearer {config.resend_api_key}",
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                },
                method="POST",
            )
            with urlopen(http_request, timeout=15) as response:
                if not 200 <= response.status < 300:
                    raise OSError("The configured email provider rejected the request.")
        elif config.smtp_configured:
            message = EmailMessage()
            message["Subject"] = "Reset your ENotes password"
            message["From"] = config.smtp_from
            message["To"] = user.email
            message.set_content(text)
            with smtplib.SMTP(config.smtp_host, config.smtp_port, timeout=15) as smtp:
                if config.smtp_starttls:
                    smtp.starttls()
                if config.smtp_username:
                    smtp.login(config.smtp_username, config.smtp_password)
                smtp.send_message(message)
        elif config.app_env != "production" and config.dev_mail_sink:
            request.app.state.mail_sink.append({"to": user.email, "reset_url": url, "created_at": int(time.time())})

    @app.get("/health")
    def health():
        return {"status": "ok", "service": "enotes-auth"}

    @app.get("/api/v1/ai/settings")
    def get_ai_settings(db: Session = Depends(get_db), user: User = Depends(current_user)):
        return {"free_only": free_only_for(db, user)}

    @app.put("/api/v1/ai/settings/free-only")
    def update_ai_free_only(body: AIFreeOnlyRequest, request: Request, db: Session = Depends(get_db), user: User = Depends(current_user)):
        limit_request(request, "ai-settings", 20, 60)
        preference = db.get(AIUserPreference, user.id)
        if preference is None:
            preference = AIUserPreference(user_id=user.id, free_only=body.enabled)
            db.add(preference)
        else:
            preference.free_only = body.enabled
        db.commit()
        return {"free_only": preference.free_only}

    @app.get("/api/v1/ai/{provider}/credential")
    def get_ai_credential(provider: str, db: Session = Depends(get_db), user: User = Depends(current_user)):
        provider = checked_provider(provider)
        row = db.scalar(select(AIProviderCredential).where(
            AIProviderCredential.user_id == user.id,
            AIProviderCredential.provider_id == provider,
        ))
        source = "missing"
        credential_usable = False
        if row is not None:
            try:
                decrypt_provider_key(config, user.id, provider, row.encrypted_key)
                credential_usable = True
                source = "user"
            except CredentialVaultUnavailable:
                credential_usable = False
        if not credential_usable and config.provider_api_key(provider):
            credential_usable = True
            source = "service"
        return {
            "provider": provider,
            "configured": credential_usable,
            "saved": row is not None,
            "credential_usable": credential_usable,
            "credential_source": source,
            "connected": False,
            "updated_at": row.updated_at if row else None,
        }

    @app.put("/api/v1/ai/{provider}/credential")
    def save_ai_credential(provider: str, body: AIProviderCredentialRequest, request: Request, db: Session = Depends(get_db), user: User = Depends(current_user)):
        provider = checked_provider(provider)
        limit_request(request, f"ai-credential-{provider}", 8, 3600)
        api_key = body.api_key.get_secret_value().strip()
        if len(api_key) < 8 or len(api_key) > 512 or any(ord(char) < 33 or ord(char) > 126 for char in api_key):
            failure(422, "invalid_provider_key_format", f"Enter a valid {provider.upper()} API key.")
        try:
            sealed = encrypt_provider_key(config, user.id, provider, api_key)
        except CredentialVaultUnavailable as error:
            failure(503, "credential_vault_unavailable", str(error))
        row = db.scalar(select(AIProviderCredential).where(
            AIProviderCredential.user_id == user.id,
            AIProviderCredential.provider_id == provider,
        ))
        if row is None:
            row = AIProviderCredential(user_id=user.id, provider_id=provider, encrypted_key=sealed, updated_at=int(time.time()))
            db.add(row)
        else:
            row.encrypted_key = sealed
            row.updated_at = int(time.time())
        db.commit()
        return {"provider": provider, "configured": True, "credential_usable": True, "credential_source": "user", "connected": False, "updated_at": row.updated_at}

    @app.delete("/api/v1/ai/{provider}/credential")
    def delete_ai_credential(provider: str, request: Request, db: Session = Depends(get_db), user: User = Depends(current_user)):
        provider = checked_provider(provider)
        limit_request(request, f"ai-credential-{provider}", 8, 3600)
        row = db.scalar(select(AIProviderCredential).where(
            AIProviderCredential.user_id == user.id,
            AIProviderCredential.provider_id == provider,
        ))
        if row is not None:
            db.delete(row)
            db.commit()
        service_configured = bool(config.provider_api_key(provider))
        return {
            "provider": provider,
            "configured": service_configured,
            "credential_usable": service_configured,
            "credential_source": "service" if service_configured else "missing",
        }

    @app.get("/api/v1/ai/{provider}/models")
    async def ai_models(provider: str, request: Request, db: Session = Depends(get_db), user: User = Depends(current_user)):
        provider = checked_provider(provider)
        limit_request(request, f"ai-models-{provider}", 30, 60)
        _row, api_key = provider_credential(db, user, provider)
        try:
            models = await list_provider_models(
                config,
                provider,
                api_key,
                ai_transport(request),
                free_only=free_only_for(db, user),
            )
        except ProviderApiError as error:
            provider_error_response(error)
        return {"provider": provider, "data": models, "free_only": free_only_for(db, user)}

    @app.post("/api/v1/ai/{provider}/test")
    async def ai_test(provider: str, body: AIChatProxyRequest, request: Request, db: Session = Depends(get_db), user: User = Depends(current_user)):
        provider = checked_provider(provider)
        limit_request(request, f"ai-test-{provider}", 15, 60)
        _row, api_key = provider_credential(db, user, provider)
        try:
            await test_provider_model(config, provider, api_key, body.model_id, free_only=free_only_for(db, user), transport=ai_transport(request))
        except ProviderApiError as error:
            provider_error_response(error)
        return {"provider": provider, "model_id": body.model_id, "connected": True}

    @app.post("/api/v1/ai/{provider}/chat")
    async def ai_chat(provider: str, body: AIChatProxyRequest, request: Request, db: Session = Depends(get_db), user: User = Depends(current_user)):
        provider = checked_provider(provider)
        limit_request(request, f"ai-chat-{provider}", 30, 60)
        _row, api_key = provider_credential(db, user, provider)
        try:
            result = await chat_provider(
                config, provider, api_key, body.model_dump(),
                free_only=free_only_for(db, user), transport=ai_transport(request),
            )
        except ProviderApiError as error:
            provider_error_response(error)
        except ValueError as error:
            failure(422, "invalid_chat_request", str(error))
        return result

    @app.post("/api/v1/auth/register", response_model=SessionResponse, status_code=201)
    def register(body: RegisterRequest, request: Request, db: Session = Depends(get_db)):
        limit_request(request, "register", config.register_limit, 3600)
        user = User(email=body.email, name=body.name, password_hash=hash_password(body.password), is_active=True, email_verified=False, created_at=int(time.time()))
        db.add(user)
        try:
            db.flush()
        except IntegrityError:
            db.rollback()
            failure(409, "email_already_registered", "An account with this email already exists.")
        return create_session(db, user)

    @app.post("/api/v1/auth/login", response_model=SessionResponse)
    def login(body: Credentials, request: Request, db: Session = Depends(get_db)):
        limit_request(request, "login", config.login_limit, 900)
        user = db.scalar(select(User).where(User.email == body.email))
        # Run the same password verifier for unknown emails to reduce account-enumeration timing leaks.
        candidate_hash = user.password_hash if user is not None else _dummy_password_hash
        valid = verify_password(candidate_hash, body.password)
        if user is None or not valid or not user.is_active:
            failure(401, "invalid_credentials", "Email or password is incorrect.")
        return create_session(db, user)

    @app.post("/api/v1/auth/refresh", response_model=SessionResponse)
    def refresh(body: RefreshRequest, request: Request, db: Session = Depends(get_db)):
        limit_request(request, "refresh", 30, 60)
        digest = hash_secret(body.refresh_token)
        now = int(time.time())
        row = db.scalar(select(AuthSession).where(AuthSession.token_hash == digest).with_for_update())
        if row is None:
            failure(401, "invalid_refresh_token", "Your session has expired. Sign in again.")
        if row.revoked_at is not None:
            revoke_family(db, row.family_id, now)
            failure(401, "refresh_token_reused", "This session was revoked. Sign in again.")
        if row.expires_at <= now:
            row.revoked_at = now
            db.commit()
            failure(401, "expired_refresh_token", "Your session has expired. Sign in again.")
        user = db.get(User, row.user_id)
        if user is None or not user.is_active:
            row.revoked_at = now
            db.commit()
            failure(401, "inactive_account", "This account is no longer active.")
        old_family = row.family_id
        row.revoked_at = now
        next_response = create_session(db, user, family_id=old_family)
        new_session = db.scalar(select(AuthSession).where(AuthSession.token_hash == hash_secret(next_response.refresh_token)))
        row.rotated_to = new_session.id if new_session else None
        db.commit()
        return next_response

    @app.post("/api/v1/auth/logout", response_model=MessageResponse)
    def logout(body: LogoutRequest, db: Session = Depends(get_db)):
        now = int(time.time())
        if body.refresh_token:
            row = db.scalar(select(AuthSession).where(AuthSession.token_hash == hash_secret(body.refresh_token)))
            if row is not None and row.revoked_at is None:
                row.revoked_at = now
                db.commit()
        return MessageResponse(message="Signed out.")

    @app.get("/api/v1/auth/me", response_model=UserResponse)
    def me(user: User = Depends(current_user)):
        return UserResponse.model_validate(user)

    @app.post("/api/v1/auth/password/forgot", response_model=MessageResponse)
    def forgot_password(body: EmailRequest, request: Request, db: Session = Depends(get_db)):
        limit_request(request, "password-reset", config.reset_limit, 3600)
        if config.app_env == "production" and not config.email_delivery_configured:
            failure(503, "password_recovery_unavailable", "Password recovery is not configured for this service yet.")
        user = db.scalar(select(User).where(User.email == body.email, User.is_active.is_(True)))
        if user is not None:
            raw_token = new_opaque_token()
            now = int(time.time())
            db.add(PasswordResetToken(user_id=user.id, token_hash=hash_secret(raw_token), created_at=now, expires_at=now + config.reset_token_minutes * 60))
            db.commit()
            try:
                send_reset_email(user, raw_token, request)
            except (OSError, smtplib.SMTPException):
                # Keep the same public response whether delivery failed or the address is unknown.
                pass
        return MessageResponse(message="If an account matches that email, reset instructions will be sent.")

    @app.post("/api/v1/auth/password/reset", response_model=MessageResponse)
    def reset_password(body: PasswordResetRequest, request: Request, db: Session = Depends(get_db)):
        limit_request(request, "password-reset-consume", 8, 900)
        now = int(time.time())
        reset = db.scalar(select(PasswordResetToken).where(PasswordResetToken.token_hash == hash_secret(body.token)).with_for_update())
        user = db.scalar(select(User).where(User.email == body.email))
        if reset is None or reset.used_at is not None or reset.expires_at <= now or user is None or reset.user_id != user.id:
            failure(400, "invalid_reset_token", "This password reset link is invalid or expired. Request a new one.")
        reset.used_at = now
        user.password_hash = hash_password(body.new_password)
        db.execute(update(AuthSession).where(AuthSession.user_id == user.id, AuthSession.revoked_at.is_(None)).values(revoked_at=now))
        db.commit()
        return MessageResponse(message="Password updated. Sign in with your new password.")

    @app.post("/api/v1/auth/password/change", response_model=MessageResponse)
    def change_password(body: PasswordChangeRequest, db: Session = Depends(get_db), user: User = Depends(current_user)):
        if not verify_password(user.password_hash, body.current_password):
            failure(401, "invalid_credentials", "Current password is incorrect.")
        if body.current_password == body.new_password:
            failure(422, "same_password", "Choose a different new password.")
        now = int(time.time())
        user.password_hash = hash_password(body.new_password)
        db.execute(update(AuthSession).where(AuthSession.user_id == user.id, AuthSession.revoked_at.is_(None)).values(revoked_at=now))
        db.commit()
        return MessageResponse(message="Password updated. Sign in again on your devices.")

    @app.delete("/api/v1/auth/me", response_model=MessageResponse)
    def delete_account(body: DeleteAccountRequest, db: Session = Depends(get_db), user: User = Depends(current_user)):
        if not verify_password(user.password_hash, body.password):
            failure(401, "invalid_credentials", "Password is incorrect.")
        db.delete(user)
        db.commit()
        return MessageResponse(message="Account deleted.")

    @app.get("/api/v1/dev/mailbox")
    def development_mailbox(email: str, request: Request):
        if config.app_env == "production" or not config.dev_mail_sink:
            failure(404, "not_found", "Not found.")
        normalized = email.strip().lower()
        messages = [item for item in request.app.state.mail_sink if item["to"] == normalized]
        return {"messages": messages[-10:]}

    @app.get("/reset-password", include_in_schema=False)
    def reset_landing():
        return JSONResponse({"message": "Open the ENotes password reset link on a device where ENotes is installed."})

    return app


_dummy_password_hash = hash_password("ENotes dummy credential never used")
app = create_app()
