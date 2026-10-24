from __future__ import annotations

from pathlib import Path

from sqlalchemy import create_engine, event
from sqlalchemy.engine import make_url
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker
from sqlalchemy.pool import StaticPool


class Base(DeclarativeBase):
    pass


def normalize_database_url(url: str, *, require_postgres: bool = False, require_tls: bool = False) -> str:
    """Normalize provider URLs for the installed psycopg 3 driver without logging secrets."""
    try:
        parsed = make_url(url)
    except Exception as error:
        raise ValueError("DATABASE_URL is not a valid database URL.") from error

    if require_postgres and parsed.get_backend_name() != "postgresql":
        raise ValueError("Production DATABASE_URL must point to a PostgreSQL database.")

    if parsed.get_backend_name() == "postgresql":
        if parsed.drivername == "postgresql":
            parsed = parsed.set(drivername="postgresql+psycopg")
        elif parsed.drivername != "postgresql+psycopg":
            raise ValueError("PostgreSQL DATABASE_URL must use the installed psycopg driver.")

        if require_tls:
            query = dict(parsed.query)
            sslmode = str(query.get("sslmode", "")).lower()
            if not sslmode:
                query["sslmode"] = "require"
            elif sslmode not in {"require", "verify-ca", "verify-full"}:
                raise ValueError("Production PostgreSQL connections must require TLS (sslmode=require or stronger).")
            parsed = parsed.set(query=query)

    return parsed.render_as_string(hide_password=False)


def build_engine(url: str) -> Engine:
    url = normalize_database_url(url)
    connect_args: dict[str, object] = {}
    options: dict[str, object] = {"pool_pre_ping": True}
    if url.startswith("sqlite:"):
        connect_args["check_same_thread"] = False
        if url in {"sqlite://", "sqlite:///:memory:"}:
            options["poolclass"] = StaticPool
        elif url.startswith("sqlite:///./"):
            db_path = Path(url.removeprefix("sqlite:///./"))
            db_path.parent.mkdir(parents=True, exist_ok=True)
    engine = create_engine(url, connect_args=connect_args, **options)
    if url.startswith("sqlite:"):
        @event.listens_for(engine, "connect")
        def enable_foreign_keys(connection, _record) -> None:
            cursor = connection.cursor()
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.close()
    return engine


def build_session_factory(engine: Engine):
    return sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
