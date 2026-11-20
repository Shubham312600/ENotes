from collections.abc import Iterator
import base64

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


@pytest.fixture
def client() -> Iterator[TestClient]:
    app = create_app(Settings(
        app_env="test", database_url="sqlite://", jwt_secret="test-only-secret-with-adequate-length",
        auto_create_schema=True, dev_mail_sink=True,
        ai_credentials_encryption_key=base64.urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode("ascii"),
    ))
    with TestClient(app) as test_client:
        yield test_client
