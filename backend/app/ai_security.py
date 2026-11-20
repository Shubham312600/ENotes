from __future__ import annotations

import base64
import binascii
import re

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .config import Settings


class CredentialVaultUnavailable(RuntimeError):
    pass


def _key(settings: Settings) -> bytes:
    raw = settings.ai_credentials_encryption_key.strip()
    if not raw:
        raise CredentialVaultUnavailable("AI provider credential storage is not configured on this service.")
    # A 32-byte key has 43 unpadded URL-safe Base64 characters, or 44
    # characters with the single canonical '=' padding character.
    if not re.fullmatch(r"[A-Za-z0-9_-]{43}=?", raw):
        raise CredentialVaultUnavailable("AI provider credential storage requires a valid URL-safe Base64 key.")
    try:
        value = base64.b64decode(raw + "=" * (-len(raw) % 4), altchars=b"-_", validate=True)
    except (ValueError, binascii.Error) as error:
        raise CredentialVaultUnavailable("AI provider credential storage is not configured correctly.") from error
    if len(value) != 32 or base64.urlsafe_b64encode(value).decode("ascii").rstrip("=") != raw.rstrip("="):
        raise CredentialVaultUnavailable("AI provider credential storage requires a 32-byte encryption key.")
    return value


def encrypt_provider_key(settings: Settings, user_id: str, provider_id: str, api_key: str) -> str:
    import secrets

    nonce = secrets.token_bytes(12)
    aad = f"{user_id}:{provider_id}:v1".encode("utf-8")
    cipher_text = AESGCM(_key(settings)).encrypt(nonce, api_key.encode("utf-8"), aad)
    return "v1." + base64.urlsafe_b64encode(nonce + cipher_text).decode("ascii").rstrip("=")


def decrypt_provider_key(settings: Settings, user_id: str, provider_id: str, sealed: str) -> str:
    try:
        version, encoded = sealed.split(".", 1)
        if version != "v1":
            raise ValueError("unsupported version")
        raw = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
        nonce, cipher_text = raw[:12], raw[12:]
        aad = f"{user_id}:{provider_id}:v1".encode("utf-8")
        return AESGCM(_key(settings)).decrypt(nonce, cipher_text, aad).decode("utf-8")
    except CredentialVaultUnavailable:
        raise
    except Exception as error:
        raise CredentialVaultUnavailable("The saved provider credential could not be opened. Replace it in AI settings.") from error
