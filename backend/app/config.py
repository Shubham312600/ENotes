from __future__ import annotations

import os
import secrets
from dataclasses import dataclass

from dotenv import load_dotenv

from .database import normalize_database_url

load_dotenv()


@dataclass(frozen=True)
class Settings:
    app_env: str = "development"
    database_url: str = "sqlite:///./data/enotes.db"
    jwt_secret: str = ""
    access_token_minutes: int = 15
    refresh_token_days: int = 30
    reset_token_minutes: int = 30
    public_app_url: str = "enotes://reset-password"
    dev_mail_sink: bool = True
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_username: str = ""
    smtp_password: str = ""
    smtp_from: str = ""
    smtp_starttls: bool = True
    resend_api_key: str = ""
    resend_from_email: str = ""
    auto_create_schema: bool = True
    login_limit: int = 8
    register_limit: int = 5
    reset_limit: int = 5

    @classmethod
    def from_environment(cls) -> "Settings":
        environment = os.getenv("APP_ENV", "development").strip().lower()
        secret = os.getenv("JWT_SECRET", "")
        if environment == "production" and (len(secret) < 32 or secret.lower().startswith(("replace-", "change-me", "your-"))):
            raise RuntimeError("JWT_SECRET must be configured with at least 32 characters in production.")
        if not secret:
            secret = secrets.token_urlsafe(48)
        raw_database_url = os.getenv("DATABASE_URL", "sqlite:///./data/enotes.db").strip()
        if environment == "production" and not raw_database_url:
            raise RuntimeError("DATABASE_URL must be configured with a persistent PostgreSQL database in production.")
        try:
            database_url = normalize_database_url(
                raw_database_url,
                require_postgres=environment == "production",
                require_tls=environment == "production",
            )
        except ValueError as error:
            raise RuntimeError(str(error)) from error
        smtp_host = os.getenv("SMTP_HOST", "")
        smtp_from = os.getenv("SMTP_FROM", "")
        resend_api_key = os.getenv("RESEND_API_KEY", "").strip()
        resend_from_email = os.getenv("RESEND_FROM_EMAIL", "").strip()
        smtp_starttls = os.getenv("SMTP_STARTTLS", "true").lower() == "true"
        if environment == "production" and smtp_host and smtp_from and not smtp_starttls:
            raise RuntimeError("Production SMTP must use STARTTLS.")
        return cls(
            app_env=environment,
            database_url=database_url,
            jwt_secret=secret,
            access_token_minutes=max(1, int(os.getenv("ACCESS_TOKEN_MINUTES", "15"))),
            refresh_token_days=max(1, int(os.getenv("REFRESH_TOKEN_DAYS", "30"))),
            reset_token_minutes=max(1, int(os.getenv("RESET_TOKEN_MINUTES", "30"))),
            public_app_url=os.getenv("PUBLIC_APP_URL", "enotes://reset-password").rstrip("?"),
            dev_mail_sink=os.getenv("DEV_MAIL_SINK", "true").lower() == "true",
            smtp_host=smtp_host,
            smtp_port=int(os.getenv("SMTP_PORT", "587")),
            smtp_username=os.getenv("SMTP_USERNAME", ""),
            smtp_password=os.getenv("SMTP_PASSWORD", ""),
            smtp_from=smtp_from,
            smtp_starttls=smtp_starttls,
            resend_api_key=resend_api_key,
            resend_from_email=resend_from_email,
            auto_create_schema=environment != "production",
        )

    @property
    def smtp_configured(self) -> bool:
        return bool(self.smtp_host and self.smtp_from)

    @property
    def resend_configured(self) -> bool:
        return bool(self.resend_api_key and self.resend_from_email)

    @property
    def email_delivery_configured(self) -> bool:
        return self.resend_configured or self.smtp_configured
