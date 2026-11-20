from __future__ import annotations

from types import SimpleNamespace

import pytest
from fastapi import HTTPException, Request

from app.config import Settings
from app.rate_limit import RateLimiter, limit_request, rate_limit_client_identity


def make_request(app_env: str, headers: list[tuple[bytes, bytes]], client: tuple[str, int] = ("render-proxy", 443)) -> Request:
    app = SimpleNamespace(state=SimpleNamespace(settings=Settings(app_env=app_env), rate_limiter=RateLimiter()))
    scope = {
        "type": "http",
        "asgi": {"version": "3.0"},
        "http_version": "1.1",
        "method": "POST",
        "scheme": "https",
        "path": "/api/v1/auth/login",
        "raw_path": b"/api/v1/auth/login",
        "query_string": b"",
        "headers": headers,
        "client": client,
        "server": ("enotes-auth-api.onrender.com", 443),
        "app": app,
    }
    return Request(scope)


def test_production_rate_limit_uses_valid_cloudflare_client_ip_and_ignores_xff() -> None:
    request = make_request("production", [
        (b"cf-connecting-ip", b"203.0.113.10"),
        (b"x-forwarded-for", b"198.51.100.1, 198.51.100.2"),
    ])
    assert rate_limit_client_identity(request) == "203.0.113.10"


def test_spoofed_or_missing_production_client_header_uses_fail_closed_shared_bucket() -> None:
    for headers in (
        [(b"x-forwarded-for", b"198.51.100.1")],
        [(b"cf-connecting-ip", b"not-an-ip"), (b"x-forwarded-for", b"198.51.100.1")],
        [(b"cf-connecting-ip", b"203.0.113.1"), (b"cf-connecting-ip", b"203.0.113.2")],
    ):
        assert rate_limit_client_identity(make_request("production", headers)) == "unverified-proxy-client"


def test_production_rate_limits_are_not_bypassable_by_changing_xff() -> None:
    limiter = RateLimiter()
    first = make_request("production", [
        (b"cf-connecting-ip", b"203.0.113.10"),
        (b"x-forwarded-for", b"192.0.2.10"),
    ])
    second = make_request("production", [
        (b"cf-connecting-ip", b"203.0.113.10"),
        (b"x-forwarded-for", b"192.0.2.99"),
    ])
    first.app.state.rate_limiter = limiter
    second.app.state.rate_limiter = limiter
    limit_request(first, "login", 1, 900)
    with pytest.raises(HTTPException) as error:
        limit_request(second, "login", 1, 900)
    assert error.value.status_code == 429


def test_development_rate_limit_uses_socket_address_not_forwarded_header() -> None:
    request = make_request("development", [(b"x-forwarded-for", b"198.51.100.1")], client=("192.0.2.8", 1234))
    assert rate_limit_client_identity(request) == "192.0.2.8"
