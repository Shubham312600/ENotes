from __future__ import annotations

from app.config import Settings


def test_provider_secret_environment_variables_load_without_being_represented(monkeypatch) -> None:
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("DATABASE_URL", "sqlite://")
    monkeypatch.setenv("JWT_SECRET", "test-only-secret-with-adequate-length")
    expected = {
        "GEMINI_API_KEY": "unit-test-gemini-token",
        "GROQ_API_KEY": "unit-test-groq-token",
        "GLM_API_KEY": "unit-test-glm-token",
    }
    for name, value in expected.items():
        monkeypatch.setenv(name, value)

    settings = Settings.from_environment()

    assert settings.provider_api_key("gemini") == expected["GEMINI_API_KEY"]
    assert settings.provider_api_key("groq") == expected["GROQ_API_KEY"]
    assert settings.provider_api_key("glm") == expected["GLM_API_KEY"]
    representation = repr(settings)
    assert all(value not in representation for value in expected.values())


def test_unknown_provider_has_no_service_managed_key() -> None:
    assert Settings().provider_api_key("xai") == ""
