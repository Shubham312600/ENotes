from __future__ import annotations

import hashlib
import secrets
import time
import uuid

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError, VerifyMismatchError

from .config import Settings

_password_hasher = PasswordHasher(time_cost=3, memory_cost=65_536, parallelism=2, hash_len=32, salt_len=16)


def hash_password(password: str) -> str:
    return _password_hasher.hash(password)


def verify_password(password_hash: str, password: str) -> bool:
    try:
        return _password_hasher.verify(password_hash, password)
    except (VerifyMismatchError, VerificationError, InvalidHashError):
        return False


def hash_secret(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def new_opaque_token() -> str:
    return secrets.token_urlsafe(48)


def issue_access_token(settings: Settings, user_id: str, session_id: str, now: int | None = None) -> str:
    current = int(time.time()) if now is None else now
    return jwt.encode(
        {"sub": user_id, "sid": session_id, "jti": str(uuid.uuid4()), "iss": "enotes-api", "aud": "enotes-android", "iat": current, "exp": current + settings.access_token_minutes * 60},
        settings.jwt_secret,
        algorithm="HS256",
    )


def decode_access_token(settings: Settings, token: str) -> dict[str, object]:
    return jwt.decode(
        token,
        settings.jwt_secret,
        algorithms=["HS256"],
        issuer="enotes-api",
        audience="enotes-android",
        options={"require": ["sub", "sid", "jti", "iss", "aud", "iat", "exp"]},
    )
