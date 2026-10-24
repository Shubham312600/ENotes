from __future__ import annotations

import re

from pydantic import BaseModel, ConfigDict, Field, field_validator


def valid_email(value: str) -> str:
    normalized = value.strip().lower()
    if len(normalized) > 254 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", normalized):
        raise ValueError("Enter a valid email address.")
    return normalized


def valid_password(value: str) -> str:
    if len(value) < 12 or len(value) > 128:
        raise ValueError("Password must be between 12 and 128 characters.")
    return value


class Credentials(BaseModel):
    email: str = Field(min_length=3, max_length=254)
    password: str = Field(min_length=1, max_length=128)

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: str) -> str:
        return valid_email(value)


class RegisterRequest(Credentials):
    name: str = Field(min_length=1, max_length=120)

    @field_validator("name")
    @classmethod
    def clean_name(cls, value: str) -> str:
        name = " ".join(value.split())
        if not name:
            raise ValueError("Enter your name.")
        return name

    @field_validator("password")
    @classmethod
    def enforce_password(cls, value: str) -> str:
        return valid_password(value)


class EmailRequest(BaseModel):
    email: str = Field(min_length=3, max_length=254)

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: str) -> str:
        return valid_email(value)


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=32, max_length=256)


class LogoutRequest(BaseModel):
    refresh_token: str | None = Field(default=None, min_length=32, max_length=256)


class PasswordResetRequest(EmailRequest):
    token: str = Field(min_length=32, max_length=256)
    new_password: str = Field(min_length=12, max_length=128)


class PasswordChangeRequest(BaseModel):
    current_password: str = Field(min_length=1, max_length=128)
    new_password: str = Field(min_length=12, max_length=128)


class DeleteAccountRequest(BaseModel):
    password: str = Field(min_length=1, max_length=128)


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    name: str
    email: str
    email_verified: bool
    created_at: int


class SessionResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserResponse


class MessageResponse(BaseModel):
    message: str
