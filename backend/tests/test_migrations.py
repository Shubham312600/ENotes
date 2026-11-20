from __future__ import annotations

from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect, text

from app.database import normalize_database_url


BACKEND_DIR = Path(__file__).resolve().parents[1]


def run_migration(database_path: Path, revision: str, monkeypatch) -> str:
    url = f"sqlite:///{database_path.as_posix()}"
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("DATABASE_URL", url)
    config = Config(str(BACKEND_DIR / "alembic.ini"))
    config.set_main_option("script_location", str(BACKEND_DIR / "migrations"))
    config.set_main_option("sqlalchemy.url", url)
    command.upgrade(config, revision)
    return url


def test_fresh_database_applies_auth_and_ai_migrations_in_order(tmp_path: Path, monkeypatch) -> None:
    url = run_migration(tmp_path / "fresh.db", "head", monkeypatch)
    engine = create_engine(url)
    try:
        tables = set(inspect(engine).get_table_names())
        assert {"auth_users", "auth_sessions", "password_reset_tokens", "ai_provider_credentials", "ai_user_preferences", "alembic_version"} <= tables
        with engine.connect() as connection:
            assert connection.scalar(text("SELECT version_num FROM alembic_version")) == "0002_ai_provider_credentials"
    finally:
        engine.dispose()


def test_database_at_revision_0001_migrates_additively_to_0002(tmp_path: Path, monkeypatch) -> None:
    database_path = tmp_path / "existing-auth.db"
    url = run_migration(database_path, "0001_auth_schema", monkeypatch)
    engine = create_engine(url)
    try:
        with engine.begin() as connection:
            connection.execute(text(
                "INSERT INTO auth_users (id, email, name, password_hash, is_active, email_verified, created_at) "
                "VALUES ('user-1', 'existing@example.com', 'Existing', 'hash', 1, 0, 1)"
            ))
    finally:
        engine.dispose()

    url = run_migration(database_path, "head", monkeypatch)
    engine = create_engine(url)
    try:
        with engine.connect() as connection:
            assert connection.scalar(text("SELECT email FROM auth_users WHERE id = 'user-1'")) == "existing@example.com"
            assert connection.scalar(text("SELECT version_num FROM alembic_version")) == "0002_ai_provider_credentials"
        assert "ai_provider_credentials" in inspect(engine).get_table_names()
    finally:
        engine.dispose()


def test_production_postgres_urls_use_psycopg_and_tls() -> None:
    normalized = normalize_database_url(
        "postgresql://enotes:pass%40word@db.example:5432/enotes",
        require_postgres=True,
        require_tls=True,
    )
    assert normalized.startswith("postgresql+psycopg://")
    assert "sslmode=require" in normalized
    assert "pass%40word" in normalized


def test_production_postgres_rejects_incompatible_driver_and_weak_tls() -> None:
    import pytest

    with pytest.raises(ValueError, match="installed psycopg"):
        normalize_database_url("postgresql+psycopg2://user:pass@db.example/db?sslmode=require", require_postgres=True, require_tls=True)
    with pytest.raises(ValueError, match="require TLS"):
        normalize_database_url("postgresql://user:pass@db.example/db?sslmode=disable", require_postgres=True, require_tls=True)
