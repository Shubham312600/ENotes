from __future__ import annotations

import base64

import pytest

from app.ai_security import CredentialVaultUnavailable, decrypt_provider_key, encrypt_provider_key
from app.config import Settings


KEY_BYTES = b"0123456789abcdef0123456789abcdef"
KEY = base64.urlsafe_b64encode(KEY_BYTES).decode("ascii").rstrip("=")


def settings(key: str = KEY) -> Settings:
    return Settings(ai_credentials_encryption_key=key)


def test_aes_gcm_credential_round_trip_supports_unpadded_and_padded_urlsafe_keys() -> None:
    for key in (KEY, base64.urlsafe_b64encode(KEY_BYTES).decode("ascii")):
        configured = settings(key)
        encrypted = encrypt_provider_key(configured, "user-1", "groq", "provider-secret")
        assert encrypted.startswith("v1.")
        assert "provider-secret" not in encrypted
        assert decrypt_provider_key(configured, "user-1", "groq", encrypted) == "provider-secret"


@pytest.mark.parametrize("key", ["", "short", "=" * 44, KEY + "==", KEY[:-1] + "+"])
def test_missing_or_malformed_encryption_key_is_rejected(key: str) -> None:
    with pytest.raises(CredentialVaultUnavailable):
        encrypt_provider_key(settings(key), "user-1", "groq", "provider-secret")


def test_wrong_length_encryption_key_is_rejected() -> None:
    key = base64.urlsafe_b64encode(b"too-short").decode("ascii").rstrip("=")
    with pytest.raises(CredentialVaultUnavailable):
        encrypt_provider_key(settings(key), "user-1", "groq", "provider-secret")


@pytest.mark.parametrize("user_id,provider_id", [("user-2", "groq"), ("user-1", "glm")])
def test_ciphertext_is_bound_to_its_user_and_provider(user_id: str, provider_id: str) -> None:
    encrypted = encrypt_provider_key(settings(), "user-1", "groq", "provider-secret")
    with pytest.raises(CredentialVaultUnavailable):
        decrypt_provider_key(settings(), user_id, provider_id, encrypted)


def test_corrupted_ciphertext_fails_authentication_without_exposing_plaintext() -> None:
    encrypted = encrypt_provider_key(settings(), "user-1", "groq", "provider-secret")
    prefix, payload = encrypted.split(".", 1)
    raw = bytearray(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    raw[-1] ^= 1
    corrupted = prefix + "." + base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")
    with pytest.raises(CredentialVaultUnavailable) as error:
        decrypt_provider_key(settings(), "user-1", "groq", corrupted)
    assert "provider-secret" not in str(error.value)


def test_decryption_with_missing_key_fails_safely() -> None:
    encrypted = encrypt_provider_key(settings(), "user-1", "groq", "provider-secret")
    with pytest.raises(CredentialVaultUnavailable) as error:
        decrypt_provider_key(settings(""), "user-1", "groq", encrypted)
    assert "provider-secret" not in str(error.value)
