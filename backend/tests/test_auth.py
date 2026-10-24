from urllib.parse import parse_qs, urlparse

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.models import AuthSession, PasswordResetToken
from app.security import hash_secret


def register(client: TestClient, email: str = "reader@example.com", password: str = "correct horse battery staple") -> dict:
    response = client.post("/api/v1/auth/register", json={"name": " Reader One ", "email": email, "password": password})
    assert response.status_code == 201, response.text
    return response.json()


def bearer(session: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {session['access_token']}"}


def test_register_login_duplicate_and_generic_bad_credentials(client: TestClient) -> None:
    first = register(client, "Reader@Example.com")
    assert first["user"]["email"] == "reader@example.com"
    assert "password_hash" not in first["user"]
    assert client.post("/api/v1/auth/register", json={"name": "Other", "email": "reader@example.com", "password": "correct horse battery staple"}).status_code == 409
    assert client.post("/api/v1/auth/login", json={"email": "reader@example.com", "password": "incorrect password"}).json()["error"]["code"] == "invalid_credentials"
    unknown = client.post("/api/v1/auth/login", json={"email": "missing@example.com", "password": "incorrect password"})
    assert unknown.status_code == 401
    assert unknown.json()["error"]["message"] == "Email or password is incorrect."


def test_validation_rejects_short_password_and_malformed_email(client: TestClient) -> None:
    short = client.post("/api/v1/auth/register", json={"name": "Reader", "email": "reader@example.com", "password": "short"})
    email = client.post("/api/v1/auth/login", json={"email": "not-an-email", "password": "some value"})
    assert short.status_code == 422
    assert email.status_code == 422
    assert "error" in short.json()


def test_authenticated_profile_and_logout_revoke_access(client: TestClient) -> None:
    session = register(client)
    assert client.get("/api/v1/auth/me", headers=bearer(session)).json()["email"] == "reader@example.com"
    logged_out = client.post("/api/v1/auth/logout", json={"refresh_token": session["refresh_token"]})
    assert logged_out.status_code == 200
    assert client.get("/api/v1/auth/me", headers=bearer(session)).status_code == 401


def test_refresh_rotates_token_and_reuse_revokes_session_family(client: TestClient) -> None:
    original = register(client)
    rotated_response = client.post("/api/v1/auth/refresh", json={"refresh_token": original["refresh_token"]})
    assert rotated_response.status_code == 200
    rotated = rotated_response.json()
    assert rotated["refresh_token"] != original["refresh_token"]
    assert client.post("/api/v1/auth/refresh", json={"refresh_token": original["refresh_token"]}).status_code == 401
    assert client.get("/api/v1/auth/me", headers=bearer(rotated)).status_code == 401


def test_expired_access_and_invalid_refresh_are_rejected() -> None:
    app = create_app(Settings(app_env="test", database_url="sqlite://", jwt_secret="test-only-secret-with-adequate-length", access_token_minutes=0, auto_create_schema=True))
    with TestClient(app) as client:
        session = register(client)
        assert client.get("/api/v1/auth/me", headers=bearer(session)).status_code == 401
        assert client.post("/api/v1/auth/refresh", json={"refresh_token": "x" * 48}).status_code == 401


def test_forgot_password_is_generic_and_reset_token_is_single_use(client: TestClient) -> None:
    register(client)
    known = client.post("/api/v1/auth/password/forgot", json={"email": "reader@example.com"})
    unknown = client.post("/api/v1/auth/password/forgot", json={"email": "missing@example.com"})
    assert known.json() == unknown.json()
    message = client.get("/api/v1/dev/mailbox", params={"email": "reader@example.com"}).json()["messages"][-1]
    query = parse_qs(urlparse(message["reset_url"]).query)
    token = query["token"][0]
    reset_payload = {"email": "reader@example.com", "token": token, "new_password": "a new secure passphrase"}
    assert client.post("/api/v1/auth/password/reset", json=reset_payload).status_code == 200
    assert client.post("/api/v1/auth/password/reset", json=reset_payload).status_code == 400
    assert client.post("/api/v1/auth/login", json={"email": "reader@example.com", "password": "a new secure passphrase"}).status_code == 200


def test_expired_password_reset_token_is_rejected(client: TestClient) -> None:
    register(client)
    client.post("/api/v1/auth/password/forgot", json={"email": "reader@example.com"})
    message = client.get("/api/v1/dev/mailbox", params={"email": "reader@example.com"}).json()["messages"][-1]
    token = parse_qs(urlparse(message["reset_url"]).query)["token"][0]
    factory = client.app.state.session_factory
    with factory() as db:
        row = db.query(PasswordResetToken).filter_by(token_hash=hash_secret(token)).one()
        row.expires_at = 0
        db.commit()
    assert client.post("/api/v1/auth/password/reset", json={"email": "reader@example.com", "token": token, "new_password": "a new secure passphrase"}).status_code == 400


def test_expired_refresh_session_is_rejected(client: TestClient) -> None:
    session = register(client)
    factory = client.app.state.session_factory
    with factory() as db:
        row = db.query(AuthSession).filter_by(token_hash=hash_secret(session["refresh_token"])).one()
        row.expires_at = 0
        db.commit()
    response = client.post("/api/v1/auth/refresh", json={"refresh_token": session["refresh_token"]})
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "expired_refresh_token"


def test_password_change_revokes_old_session_and_allows_new_password(client: TestClient) -> None:
    session = register(client)
    changed = client.post("/api/v1/auth/password/change", headers=bearer(session), json={"current_password": "correct horse battery staple", "new_password": "a new secure passphrase"})
    assert changed.status_code == 200
    assert client.get("/api/v1/auth/me", headers=bearer(session)).status_code == 401
    assert client.post("/api/v1/auth/login", json={"email": "reader@example.com", "password": "a new secure passphrase"}).status_code == 200


def test_account_deletion_requires_password_and_removes_user_sessions(client: TestClient) -> None:
    session = register(client)
    wrong = client.request("DELETE", "/api/v1/auth/me", headers=bearer(session), json={"password": "wrong password"})
    assert wrong.status_code == 401
    deleted = client.request("DELETE", "/api/v1/auth/me", headers=bearer(session), json={"password": "correct horse battery staple"})
    assert deleted.status_code == 200
    assert client.post("/api/v1/auth/login", json={"email": "reader@example.com", "password": "correct horse battery staple"}).status_code == 401
    with client.app.state.session_factory() as db:
        assert db.query(AuthSession).count() == 0


def test_database_persists_between_app_instances(tmp_path) -> None:
    database_url = f"sqlite:///{tmp_path / 'auth.sqlite3'}"
    config = Settings(app_env="test", database_url=database_url, jwt_secret="test-only-secret-with-adequate-length", auto_create_schema=True)
    with TestClient(create_app(config)) as first:
        register(first)
    with TestClient(create_app(config)) as second:
        assert second.post("/api/v1/auth/login", json={"email": "reader@example.com", "password": "correct horse battery staple"}).status_code == 200


def test_authentication_attempts_are_rate_limited() -> None:
    app = create_app(Settings(app_env="development", database_url="sqlite://", jwt_secret="test-only-secret-with-adequate-length", login_limit=1, auto_create_schema=True))
    with TestClient(app) as client:
        body = {"email": "reader@example.com", "password": "incorrect password"}
        assert client.post("/api/v1/auth/login", json=body).status_code == 401
        limited = client.post("/api/v1/auth/login", json=body)
        assert limited.status_code == 429
        assert limited.headers["retry-after"]


def test_dev_mailbox_is_unavailable_in_production() -> None:
    app = create_app(Settings(app_env="production", database_url="sqlite://", jwt_secret="test-only-secret-with-adequate-length", auto_create_schema=True, dev_mail_sink=True))
    with TestClient(app) as client:
        assert client.get("/api/v1/dev/mailbox", params={"email": "reader@example.com"}).status_code == 404


def test_production_rejects_example_signing_secret(monkeypatch) -> None:
    from app.config import Settings

    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("JWT_SECRET", "replace-with-a-long-random-secret-before-deployment")
    with pytest.raises(RuntimeError, match="JWT_SECRET"):
        Settings.from_environment()


def test_production_requires_tls_postgres(monkeypatch) -> None:
    from app.config import Settings

    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("JWT_SECRET", "a-long-random-signing-secret-for-production-tests")
    monkeypatch.delenv("DATABASE_URL", raising=False)
    with pytest.raises(RuntimeError, match="DATABASE_URL"):
        Settings.from_environment()

    monkeypatch.setenv("DATABASE_URL", "sqlite:///./data/enotes.db")
    with pytest.raises(RuntimeError, match="PostgreSQL"):
        Settings.from_environment()

    monkeypatch.setenv("DATABASE_URL", "postgresql://enotes:example@ep-test.neon.tech/enotes?sslmode=disable")
    with pytest.raises(RuntimeError, match="TLS"):
        Settings.from_environment()

    monkeypatch.setenv("DATABASE_URL", "postgresql://enotes:p%40ss@ep-test.neon.tech/enotes?sslmode=require")
    config = Settings.from_environment()
    assert config.database_url.startswith("postgresql+psycopg://")
    assert "sslmode=require" in config.database_url


def test_production_reports_password_recovery_unavailable_without_email_provider() -> None:
    app = create_app(Settings(app_env="production", database_url="sqlite://", jwt_secret="test-only-secret-with-adequate-length", auto_create_schema=True))
    with TestClient(app) as client:
        response = client.post("/api/v1/auth/password/forgot", json={"email": "reader@example.com"})
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "password_recovery_unavailable"


def test_password_reset_mail_can_use_https_provider(monkeypatch) -> None:
    import json
    from app import main as auth_main

    captured: dict[str, object] = {}

    class FakeResponse:
        status = 200

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

    def fake_urlopen(request, timeout):
        captured["url"] = request.full_url
        captured["authorization"] = request.get_header("Authorization")
        captured["timeout"] = timeout
        captured["payload"] = json.loads(request.data.decode("utf-8"))
        return FakeResponse()

    monkeypatch.setattr(auth_main, "urlopen", fake_urlopen)
    app = create_app(Settings(
        app_env="test",
        database_url="sqlite://",
        jwt_secret="test-only-secret-with-adequate-length",
        auto_create_schema=True,
        resend_api_key="re_test_key",
        resend_from_email="ENotes <no-reply@example.com>",
    ))
    with TestClient(app) as client:
        register(client)
        response = client.post("/api/v1/auth/password/forgot", json={"email": "reader@example.com"})
        assert response.status_code == 200

    assert captured["url"] == "https://api.resend.com/emails"
    assert captured["authorization"] == "Bearer re_test_key"
    assert captured["timeout"] == 15
    payload = captured["payload"]
    assert payload["to"] == ["reader@example.com"]
    assert payload["from"] == "ENotes <no-reply@example.com>"
    assert "enotes://reset-password" in payload["text"]
