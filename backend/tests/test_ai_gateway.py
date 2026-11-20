from __future__ import annotations

import base64

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.main import create_app
from app.models import AIProviderCredential
from app.ai_proxy import classify_upstream_error


def sign_in(client: TestClient, email: str = "ai@example.com") -> dict[str, str]:
    result = client.post("/api/v1/auth/register", json={
        "name": "AI User", "email": email, "password": "correct horse battery staple",
    })
    assert result.status_code == 201
    return {"Authorization": f"Bearer {result.json()['access_token']}"}


def mock_transport(client: TestClient, handler) -> None:
    client.app.state.ai_http_transport = httpx.MockTransport(handler)


def save_groq_key(client: TestClient, headers: dict[str, str], key: str = "gsk-test-secret-value"):
    return client.put("/api/v1/ai/groq/credential", headers=headers, json={"api_key": key})


@pytest.mark.parametrize("provider,header_name", [
    ("gemini", "x-goog-api-key"),
    ("groq", "Authorization"),
    ("glm", "Authorization"),
])
def test_service_managed_provider_keys_are_isolated_and_never_returned(provider: str, header_name: str) -> None:
    from dataclasses import replace

    test_key = f"service-managed-{provider}-test-secret"
    field = {"gemini": "gemini_api_key", "groq": "groq_api_key", "glm": "glm_api_key"}[provider]
    settings = replace(Settings(
        app_env="test",
        database_url="sqlite://",
        jwt_secret="test-only-secret-with-adequate-length",
        auto_create_schema=True,
        ai_credentials_encryption_key=base64.urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode("ascii").rstrip("="),
    ), **{field: SecretStr(test_key)})

    with TestClient(create_app(settings)) as client:
        headers = sign_in(client, f"service-key-{provider}@example.com")
        status = client.get(f"/api/v1/ai/{provider}/credential", headers=headers)
        assert status.status_code == 200
        assert status.json()["configured"] is True
        assert status.json()["saved"] is False
        assert status.json()["credential_source"] == "service"
        assert status.json()["connected"] is False
        assert test_key not in status.text

        def handler(request: httpx.Request):
            if provider == "glm":
                if request.url.host == "docs.z.ai":
                    assert "Authorization" not in request.headers
                    return httpx.Response(200, json=_provider_model_payload("glm", ["service-model"]))
                assert request.url.host == "api.z.ai"
                assert request.headers[header_name] == f"Bearer {test_key}"
                assert request.url.path == "/api/paas/v4/chat/completions"
                return httpx.Response(200, json=_provider_chat_payload(provider))
            assert request.headers[header_name] == (test_key if header_name != "Authorization" else f"Bearer {test_key}")
            if provider == "gemini":
                assert request.url.host == "generativelanguage.googleapis.com"
                if request.method == "GET":
                    return httpx.Response(200, json={"models": [{
                        "name": "models/service-model-version", "baseModelId": "service-model",
                        "supportedGenerationMethods": ["generateContent"],
                    }]})
                assert request.url.path == "/v1beta/models/service-model:generateContent"
                return httpx.Response(200, json=_provider_chat_payload(provider))
            if provider == "groq":
                assert request.url.host == "api.groq.com"
                if request.method == "GET":
                    return httpx.Response(200, json={"data": [{"id": "service-model", "active": True}]})
                assert request.url.path == "/openai/v1/chat/completions"
                return httpx.Response(200, json=_provider_chat_payload(provider))
            raise AssertionError(f"Unexpected provider: {provider}")

        mock_transport(client, handler)
        models = client.get(f"/api/v1/ai/{provider}/models", headers=headers)
        assert models.status_code == 200, models.text
        assert [item["id"] for item in models.json()["data"]] == ["service-model"]
        assert test_key not in models.text
        tested = client.post(f"/api/v1/ai/{provider}/test", headers=headers, json={
            "model_id": "service-model",
            "messages": [{"role": "user", "content": "Connection test. Reply OK."}],
            "system_instruction": "Reply only with the word OK.",
        })
        assert tested.status_code == 200, tested.text
        assert tested.json() == {"provider": provider, "model_id": "service-model", "connected": True}
        assert test_key not in tested.text


def test_ai_gateway_requires_existing_enotes_authentication(client: TestClient) -> None:
    assert client.get("/api/v1/ai/groq/models").status_code == 401
    assert client.get("/api/v1/ai/groq/models", headers={"Authorization": "Basic invalid"}).status_code == 401


def test_provider_key_is_encrypted_and_never_returned_to_android(client: TestClient) -> None:
    headers = sign_in(client)
    raw_key = "gsk-never-return-this-secret"
    saved = client.put("/api/v1/ai/groq/credential", headers=headers, json={"api_key": raw_key})
    assert saved.status_code == 200
    assert raw_key not in saved.text
    credential_status = client.get("/api/v1/ai/groq/credential", headers=headers).json()
    assert credential_status["configured"] is True
    assert credential_status["saved"] is True
    assert credential_status["credential_usable"] is True
    with client.app.state.session_factory() as db:
        row = db.query(AIProviderCredential).filter_by(provider_id="groq").one()
        assert raw_key not in row.encrypted_key
        assert row.encrypted_key.startswith("v1.")
    assert client.delete("/api/v1/ai/groq/credential", headers=headers).json()["configured"] is False


def test_malformed_groq_key_is_rejected_without_echoing_the_value(client: TestClient) -> None:
    headers = sign_in(client)
    malformed_key = "gsk-has whitespace"
    response = client.put("/api/v1/ai/groq/credential", headers=headers, json={"api_key": malformed_key})
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_provider_key_format"
    assert malformed_key not in response.text


def test_groq_model_list_uses_exact_groq_bearer_auth_and_filters_inactive_models(client: TestClient) -> None:
    headers = sign_in(client)
    assert save_groq_key(client, headers).status_code == 200

    def handler(request: httpx.Request):
        assert str(request.url) == "https://api.groq.com/openai/v1/models"
        assert request.headers["Authorization"] == "Bearer gsk-test-secret-value"
        return httpx.Response(200, json={"data": [
            {"id": "llama3-test-chat", "active": True},
            {"id": "inactive-model", "active": False},
        ]})

    mock_transport(client, handler)
    response = client.get("/api/v1/ai/groq/models", headers=headers)
    assert response.status_code == 200
    assert [item["id"] for item in response.json()["data"]] == ["llama3-test-chat"]
    assert response.json()["data"][0]["pricing_status"] == "unknown"


@pytest.mark.parametrize("status,expected_code,expected_text", [
    (401, "invalid_api_key", "Groq rejected your API key. Verify your key in the Groq Console."),
    (403, "permission_denied", "Groq denied access for this key or model. Check key and model access in the Groq Console."),
])
def test_groq_auth_errors_are_classified_without_becoming_gemini_errors(client: TestClient, status: int, expected_code: str, expected_text: str) -> None:
    headers = sign_in(client)
    assert save_groq_key(client, headers).status_code == 200
    mock_transport(client, lambda _request: httpx.Response(status, json={"error": {"message": "provider auth failure"}}))
    response = client.get("/api/v1/ai/groq/models", headers=headers)
    assert response.status_code == status
    assert response.json()["error"]["provider"] == "groq"
    assert response.json()["error"]["code"] == expected_code
    assert response.json()["error"]["message"] == expected_text
    assert "Gemini" not in response.text


def test_groq_429_separates_rate_limits_from_quota_errors() -> None:
    assert classify_upstream_error("groq", 429, {"error": {"message": "Too many requests"}}, "chat").code == "rate_limited"
    assert classify_upstream_error("groq", 429, {"error": {"code": "insufficient_quota", "message": "quota exceeded"}}, "chat").code == "quota_exhausted"
    assert classify_upstream_error("groq", 403, {"error": {"message": "billing usage limit reached"}}, "chat").code == "quota_exhausted"
    assert classify_upstream_error("groq", 401, {"error": {"message": "API key has been revoked"}}, "models").code == "invalid_api_key"


def test_gemini_400_api_key_invalid_reason_is_not_reported_as_a_generic_error() -> None:
    payload = {"error": {
        "code": 400,
        "message": "API key not valid. Please pass a valid API key.",
        "status": "INVALID_ARGUMENT",
        "details": [{
            "@type": "type.googleapis.com/google.rpc.ErrorInfo",
            "reason": "API_KEY_INVALID",
            "domain": "googleapis.com",
        }],
    }}
    error = classify_upstream_error("gemini", 400, payload, "models")
    assert error.code == "invalid_api_key"
    assert error.provider == "gemini"
    assert error.message == "Gemini rejected your API key. Verify it in Google AI Studio."
    # Do not let text in one provider's error classify another provider's key.
    assert classify_upstream_error("groq", 400, payload, "models").code == "unknown_error"


def test_gemini_model_list_surfaces_nested_invalid_key_error_as_gemini(client: TestClient) -> None:
    headers = sign_in(client)
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200
    mock_transport(client, lambda _request: httpx.Response(400, json={"error": {
        "code": 400,
        "message": "API key not valid. Please pass a valid API key.",
        "status": "INVALID_ARGUMENT",
        "details": [{"reason": "API_KEY_INVALID"}],
    }}))
    response = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert response.status_code == 400
    assert response.json()["error"]["provider"] == "gemini"
    assert response.json()["error"]["code"] == "invalid_api_key"
    assert "Gemini rejected" in response.json()["error"]["message"]


def test_gemini_403_access_failure_has_provider_specific_guidance() -> None:
    error = classify_upstream_error("gemini", 403, {"error": {
        "status": "PERMISSION_DENIED",
        "message": "API access is restricted for this project.",
    }}, "models")
    assert error.provider == "gemini"
    assert error.code == "permission_denied"
    assert "Google AI Studio" in error.message


def test_model_test_and_chat_make_real_provider_calls_through_fixed_groq_host(client: TestClient) -> None:
    headers = sign_in(client)
    assert save_groq_key(client, headers).status_code == 200
    observed: list[str] = []

    def handler(request: httpx.Request):
        observed.append(f"{request.method} {request.url}")
        assert request.url.host == "api.groq.com"
        assert request.headers["Authorization"] == "Bearer gsk-test-secret-value"
        if request.url.path.endswith("/models"):
            return httpx.Response(200, json={"data": [{"id": "llama3-test-chat", "active": True}]})
        payload = __import__("json").loads(request.content)
        assert payload["model"] == "llama3-test-chat"
        return httpx.Response(200, json={"id": "chat-test", "model": "llama3-test-chat", "choices": [{"message": {"content": "OK"}}]})

    mock_transport(client, handler)
    test = client.post("/api/v1/ai/groq/test", headers=headers, json={"model_id": "llama3-test-chat", "messages": [{"role": "user", "content": "test"}]})
    assert test.status_code == 200, test.text
    chat = client.post("/api/v1/ai/groq/chat", headers=headers, json={
        "model_id": "llama3-test-chat", "system_instruction": "Be concise.", "messages": [{"role": "user", "content": "hello"}],
    })
    assert chat.status_code == 200, chat.text
    assert len(observed) == 4
    assert any("GET https://api.groq.com/openai/v1/models" in item for item in observed)
    assert any("POST https://api.groq.com/openai/v1/chat/completions" in item for item in observed)


def test_gemini_uses_exact_v1beta_paths_and_its_own_api_key_header(client: TestClient) -> None:
    headers = sign_in(client)
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200

    def handler(request: httpx.Request):
        assert request.url.host == "generativelanguage.googleapis.com"
        assert request.headers["x-goog-api-key"] == "gemini-test-secret-value"
        assert "Authorization" not in request.headers
        if request.method == "GET":
            assert request.url.path == "/v1beta/models"
            return httpx.Response(200, json={"models": [{
                "name": "models/gemini-versioned-model-001",
                "baseModelId": "gemini-test-model",
                "supportedGenerationMethods": ["generateContent"],
            }]})
        assert request.url.path == "/v1beta/models/gemini-test-model:generateContent"
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": "OK"}]}}]})

    mock_transport(client, handler)
    models = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert models.status_code == 200
    assert [item["id"] for item in models.json()["data"]] == ["gemini-test-model"]
    test = client.post("/api/v1/ai/gemini/test", headers=headers, json={"model_id": "gemini-test-model", "messages": [{"role": "user", "content": "test"}]})
    assert test.status_code == 200 and test.json()["connected"] is True


def test_gemini_discovery_uses_base_model_ids_and_omits_unsupported_models(client: TestClient) -> None:
    headers = sign_in(client, "gemini-base-model-id@example.com")
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200

    def handler(request: httpx.Request):
        assert request.method == "GET"
        assert request.url.path == "/v1beta/models"
        assert request.headers["x-goog-api-key"] == "gemini-test-secret-value"
        return httpx.Response(200, json={"models": [
            {
                "name": "models/gemini-fast-001",
                "baseModelId": "gemini-fast",
                "supportedGenerationMethods": ["generateContent"],
            },
            {
                "name": "models/gemini-embed-001",
                "baseModelId": "gemini-embed",
                "supportedGenerationMethods": ["embedContent"],
            },
            {
                "name": "models/gemini-missing-base-id-001",
                "supportedGenerationMethods": ["generateContent"],
            },
            {
                "name": "models/gemini-fast-preview-002",
                "baseModelId": "gemini-fast",
                "supportedGenerationMethods": ["generateContent"],
            },
        ]})

    mock_transport(client, handler)
    response = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert response.status_code == 200, response.text
    assert [item["id"] for item in response.json()["data"]] == ["gemini-fast"]


def _gemini_catalog_record(model_id: str, methods: list[str] | None = None) -> dict:
    return {
        "name": f"models/{model_id}-versioned",
        "baseModelId": model_id,
        "supportedGenerationMethods": methods if methods is not None else ["generateContent"],
    }


def test_gemini_model_discovery_follows_all_pages_and_includes_page_two_models(client: TestClient) -> None:
    headers = sign_in(client, "gemini-pagination@example.com")
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-pagination-test-key"}).status_code == 200
    calls: list[httpx.Request] = []
    pages = {
        None: {"models": [_gemini_catalog_record("page-one-model")], "nextPageToken": "opaque-page-token-one"},
        "opaque-page-token-one": {"models": [_gemini_catalog_record("page-two-model")], "nextPageToken": "opaque-page-token-two"},
        "opaque-page-token-two": {"models": [_gemini_catalog_record("page-three-model")]},
    }

    def handler(request: httpx.Request):
        calls.append(request)
        assert request.url.path == "/v1beta/models"
        assert request.headers["x-goog-api-key"] == "gemini-pagination-test-key"
        return httpx.Response(200, json=pages[request.url.params.get("pageToken")])

    mock_transport(client, handler)
    response = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert response.status_code == 200, response.text
    assert [item["id"] for item in response.json()["data"]] == [
        "page-one-model", "page-three-model", "page-two-model",
    ]
    assert len(calls) == 3
    assert calls[0].url.params.get("pageToken") is None
    assert calls[1].url.params["pageToken"] == "opaque-page-token-one"
    assert calls[2].url.params["pageToken"] == "opaque-page-token-two"


@pytest.mark.parametrize("payload,expected_ids", [
    ({"models": []}, []),
    ({"models": [_gemini_catalog_record("single-page-model")]}, ["single-page-model"]),
])
def test_gemini_model_discovery_accepts_empty_and_single_page_without_token(
    client: TestClient, payload: dict, expected_ids: list[str],
) -> None:
    headers = sign_in(client, f"gemini-terminal-page-{len(expected_ids)}@example.com")
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200
    calls: list[httpx.Request] = []
    mock_transport(client, lambda request: (calls.append(request), httpx.Response(200, json=payload))[1])
    response = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert response.status_code == 200, response.text
    assert [item["id"] for item in response.json()["data"]] == expected_ids
    assert len(calls) == 1


def test_gemini_repeated_page_token_fails_safely_and_diagnostics_hide_secrets(caplog, client: TestClient) -> None:
    headers = sign_in(client, "gemini-repeated-token@example.com")
    api_key = "gemini-do-not-log-this-key"
    page_token = "opaque-do-not-log-this-token"
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": api_key}).status_code == 200
    calls: list[httpx.Request] = []

    def handler(request: httpx.Request):
        calls.append(request)
        if len(calls) == 1:
            return httpx.Response(200, json={
                "models": [_gemini_catalog_record("private-model-id-do-not-log")],
                "nextPageToken": page_token,
            })
        return httpx.Response(200, json={"models": [], "nextPageToken": page_token})

    mock_transport(client, handler)
    with caplog.at_level("INFO"):
        response = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "model_discovery_unavailable"
    assert page_token not in response.text
    assert len(calls) == 2
    log_text = caplog.text
    assert api_key not in log_text
    assert page_token not in log_text
    assert "private-model-id-do-not-log" not in log_text
    assert "[REDACTED]" in log_text
    assert '"pages_retrieved":2' in log_text


def test_gemini_model_discovery_stops_at_configured_page_limit(monkeypatch, client: TestClient) -> None:
    from app import ai_proxy

    monkeypatch.setattr(ai_proxy, "GEMINI_MODELS_MAX_PAGES", 2)
    headers = sign_in(client, "gemini-page-limit@example.com")
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200
    calls: list[httpx.Request] = []

    def handler(request: httpx.Request):
        calls.append(request)
        next_token = "limit-token-one" if len(calls) == 1 else "limit-token-two"
        return httpx.Response(200, json={"models": [], "nextPageToken": next_token})

    mock_transport(client, handler)
    response = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "model_discovery_unavailable"
    assert len(calls) == 2
    assert response.json().get("data") is None
    assert "limit-token" not in response.text


def test_gemini_free_only_uses_exact_allowlist_and_logs_only_safe_counts(caplog) -> None:
    free = '{"gemini":["verified-free"]}'
    paid = '{"gemini":["paid-model"]}'
    with _ai_client_with_allowlists(free, paid) as client:
        headers = sign_in(client, "gemini-exact-allowlist@example.com")
        assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-do-not-log-this-key"}).status_code == 200
        assert client.put("/api/v1/ai/settings/free-only", headers=headers, json={"enabled": True}).status_code == 200
        mock_transport(client, lambda _request: httpx.Response(200, json={"models": [
            _gemini_catalog_record("verified-free"),
            _gemini_catalog_record("Verified-free"),
            _gemini_catalog_record("verified-free-preview"),
            _gemini_catalog_record("paid-model"),
            _gemini_catalog_record("unknown-model"),
            {"baseModelId": "missing-methods"},
            _gemini_catalog_record("embedding-only", ["embedContent"]),
            {"supportedGenerationMethods": ["generateContent"]},
            {"baseModelId": "  ", "supportedGenerationMethods": ["generateContent"]},
            None,
        ]}))
        with caplog.at_level("INFO", logger="app.ai_proxy"):
            response = client.get("/api/v1/ai/gemini/models", headers=headers)
        assert response.status_code == 200, response.text
        assert response.json()["data"] == [{"id": "verified-free", "pricing_status": "verified_free"}]
        assert '"upstream_records_received":10' in caplog.text
        assert '"valid_base_model_id_records":7' in caplog.text
        assert '"generate_content_records":7' in caplog.text
        assert '"rejected_unsupported_or_malformed_records":5' in caplog.text
        assert '"verified_free_models":1' in caplog.text
        assert '"pricing_filtered_unknown":3' in caplog.text
        assert '"pricing_filtered_paid":1' in caplog.text
        assert "gemini-do-not-log-this-key" not in caplog.text
        for model_id in ("verified-free", "Verified-free", "paid-model", "unknown-model"):
            assert model_id not in caplog.text


@pytest.mark.parametrize("payload", [
    {"models": "not-an-array"},
    ["not-an-object"],
])
def test_gemini_model_discovery_rejects_malformed_or_unexpected_success_responses(client: TestClient, payload) -> None:
    headers = sign_in(client, f"gemini-malformed-{type(payload).__name__}@example.com")
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200
    mock_transport(client, lambda _request: httpx.Response(200, json=payload))
    response = client.get("/api/v1/ai/gemini/models", headers=headers)
    assert response.status_code == 502
    assert response.json()["error"]["provider"] == "gemini"
    assert response.json()["error"]["code"] == "unknown_error"
    assert "not-an-array" not in response.text


def test_gemini_unsupported_generate_content_model_has_actionable_error(client: TestClient) -> None:
    headers = sign_in(client, "gemini-unsupported-model@example.com")
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200

    def handler(request: httpx.Request):
        if request.method == "GET":
            return httpx.Response(200, json={"models": [{
                "name": "models/gemini-text-001", "baseModelId": "gemini-text",
                "supportedGenerationMethods": ["generateContent"],
            }]})
        assert request.url.path == "/v1beta/models/gemini-text:generateContent"
        assert request.headers["x-goog-api-key"] == "gemini-test-secret-value"
        return httpx.Response(400, json={"error": {
            "code": 400,
            "status": "INVALID_ARGUMENT",
            "message": "Model is not supported for generateContent on this API version.",
        }})

    mock_transport(client, handler)
    response = client.post("/api/v1/ai/gemini/chat", headers=headers, json={
        "model_id": "gemini-text", "messages": [{"role": "user", "content": "hello"}],
    })
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "model_unavailable"
    assert response.json()["error"]["provider"] == "gemini"
    assert "not supported for generateContent" not in response.text


@pytest.mark.parametrize("provider", ["gemini", "glm"])
def test_malformed_successful_chat_response_is_not_returned_as_success(client: TestClient, provider: str) -> None:
    headers = sign_in(client, f"malformed-chat-{provider}@example.com")
    secret = f"{provider}-test-secret-value"
    assert client.put(f"/api/v1/ai/{provider}/credential", headers=headers, json={"api_key": secret}).status_code == 200

    def handler(request: httpx.Request):
        if provider == "gemini":
            assert request.headers["x-goog-api-key"] == secret
            if request.method == "GET":
                return httpx.Response(200, json={"models": [{
                    "name": "models/gemini-model-001", "baseModelId": "gemini-model",
                    "supportedGenerationMethods": ["generateContent"],
                }]})
            return httpx.Response(200, json={"candidates": [{"content": {"parts": "bad"}}]})
        assert request.url == "https://api.z.ai/api/paas/v4/chat/completions"
        assert request.headers["Authorization"] == f"Bearer {secret}"
        return httpx.Response(200, json={"choices": [{"index": 0, "message": "bad"}]})

    mock_transport(client, handler)
    response = client.post(f"/api/v1/ai/{provider}/chat", headers=headers, json={
        "model_id": "gemini-model" if provider == "gemini" else "glm-model",
        "messages": [{"role": "user", "content": "hello"}],
    })
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "malformed_response"
    assert response.json()["error"]["provider"] == provider
    assert secret not in response.text


def test_glm_connection_test_parses_documented_chat_completion_shape(client: TestClient) -> None:
    headers = sign_in(client, "glm-test-response@example.com")
    secret = "glm-test-secret-value"
    assert client.put("/api/v1/ai/glm/credential", headers=headers, json={"api_key": secret}).status_code == 200

    def handler(request: httpx.Request):
        assert request.url == "https://api.z.ai/api/paas/v4/chat/completions"
        assert request.headers["Authorization"] == f"Bearer {secret}"
        body = __import__("json").loads(request.content)
        assert body["model"] == "glm-account-model"
        return httpx.Response(200, json={"choices": [{"message": {"role": "assistant", "content": "OK"}}]})

    mock_transport(client, handler)
    response = client.post("/api/v1/ai/glm/test", headers=headers, json={
        "model_id": "glm-account-model", "messages": [{"role": "user", "content": "test"}],
    })
    assert response.status_code == 200, response.text
    assert response.json() == {"provider": "glm", "model_id": "glm-account-model", "connected": True}


def test_empty_groq_model_list_is_returned_empty_without_selecting_a_model(client: TestClient) -> None:
    headers = sign_in(client)
    assert save_groq_key(client, headers).status_code == 200
    mock_transport(client, lambda _request: httpx.Response(200, json={"data": []}))
    response = client.get("/api/v1/ai/groq/models", headers=headers)
    assert response.status_code == 200
    assert response.json()["data"] == []


def test_unavailable_model_and_free_only_enforcement(client: TestClient) -> None:
    headers = sign_in(client)
    assert save_groq_key(client, headers).status_code == 200

    def handler(request: httpx.Request):
        if request.url.path.endswith("/models"):
            return httpx.Response(200, json={"data": [{"id": "groq-listed-but-unverified", "active": True}]})
        return httpx.Response(200, json={"choices": [{"message": {"content": "OK"}}]})

    mock_transport(client, handler)
    missing = client.post("/api/v1/ai/groq/test", headers=headers, json={"model_id": "invented-model", "messages": [{"role": "user", "content": "test"}]})
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "model_unavailable"

    setting = client.put("/api/v1/ai/settings/free-only", headers=headers, json={"enabled": True})
    assert setting.status_code == 200 and setting.json()["free_only"] is True
    blocked = client.post("/api/v1/ai/groq/test", headers=headers, json={"model_id": "groq-listed-but-unverified", "messages": [{"role": "user", "content": "test"}]})
    assert blocked.status_code == 403
    assert blocked.json()["error"]["code"] == "model_not_verified_free"


def test_free_only_accepts_only_server_allowlisted_model() -> None:
    app = create_app(Settings(
        app_env="test", database_url="sqlite://", jwt_secret="test-only-secret-with-adequate-length", auto_create_schema=True,
        ai_credentials_encryption_key=base64.urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode("ascii"),
        ai_verified_free_models='{"groq":["verified-free-test-model"]}',
    ))
    with TestClient(app) as client:
        headers = sign_in(client, "verified@example.com")
        assert save_groq_key(client, headers).status_code == 200

        def handler(request: httpx.Request):
            if request.url.path.endswith("/models"):
                return httpx.Response(200, json={"data": [{"id": "verified-free-test-model", "active": True}]})
            return httpx.Response(200, json={"choices": [{"message": {"content": "OK"}}]})

        mock_transport(client, handler)
        assert client.put("/api/v1/ai/settings/free-only", headers=headers, json={"enabled": True}).status_code == 200
        result = client.post("/api/v1/ai/groq/test", headers=headers, json={"model_id": "verified-free-test-model", "messages": [{"role": "user", "content": "test"}]})
        assert result.status_code == 200


def test_model_list_reports_only_operator_verified_paid_or_free_prices() -> None:
    settings = Settings(
        ai_verified_free_models='{"groq":["free-model","conflict-model"]}',
        ai_verified_paid_models='{"groq":["paid-model","conflict-model"]}',
    )
    from app.ai_proxy import list_provider_models

    async def run():
        transport = httpx.MockTransport(lambda _request: httpx.Response(200, json={"data": [
            {"id": "free-model"}, {"id": "paid-model"}, {"id": "unknown-model"}, {"id": "conflict-model"},
        ]}))
        return await list_provider_models(settings, "groq", "test-key", transport)

    results = __import__("asyncio").run(run())
    assert {item["id"]: item["pricing_status"] for item in results} == {
        "free-model": "verified_free", "paid-model": "paid", "unknown-model": "unknown", "conflict-model": "unknown",
    }


def test_provider_keys_and_errors_are_isolated_between_groq_and_gemini(client: TestClient) -> None:
    headers = sign_in(client)
    assert save_groq_key(client, headers).status_code == 200
    assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "gemini-test-secret-value"}).status_code == 200

    def handler(request: httpx.Request):
        if request.url.host == "api.groq.com":
            assert request.headers["Authorization"] == "Bearer gsk-test-secret-value"
            return httpx.Response(401, json={"error": {"message": "invalid"}})
        assert request.url.host == "generativelanguage.googleapis.com"
        assert request.headers["x-goog-api-key"] == "gemini-test-secret-value"
        assert "Authorization" not in request.headers
        return httpx.Response(400, json={"error": {"message": "bad model"}})

    mock_transport(client, handler)
    groq = client.get("/api/v1/ai/groq/models", headers=headers).json()["error"]
    gemini = client.get("/api/v1/ai/gemini/models", headers=headers).json()["error"]
    assert groq["provider"] == "groq" and groq["message"].startswith("Groq rejected")
    assert gemini["provider"] == "gemini" and "Gemini" in gemini["message"]


def test_glm_model_discovery_uses_official_openapi_without_sending_the_api_key(client: TestClient) -> None:
    headers = sign_in(client)
    secret = "glm-test-secret-value"
    assert client.put("/api/v1/ai/glm/credential", headers=headers, json={"api_key": secret}).status_code == 200

    calls: list[httpx.Request] = []

    def handler(request: httpx.Request):
        calls.append(request)
        assert str(request.url) == "https://docs.z.ai/openapi.json"
        assert "Authorization" not in request.headers
        return httpx.Response(200, json=_provider_model_payload("glm", ["glm-documentation-model", "glm-second-model"]))

    mock_transport(client, handler)
    response = client.get("/api/v1/ai/glm/models", headers=headers)
    assert response.status_code == 200, response.text
    assert response.json()["data"] == [
        {"id": "glm-documentation-model", "pricing_status": "unknown"},
        {"id": "glm-second-model", "pricing_status": "unknown"},
    ]
    assert len(calls) == 1
    assert secret not in response.text


@pytest.mark.parametrize("payload", [
    {"components": {"schemas": {"ChatCompletionTextRequest": {"properties": {"model": {"enum": "bad"}}}}}},
    {"components": {"schemas": {}}},
    [],
])
def test_glm_model_discovery_rejects_malformed_official_schema(client: TestClient, payload) -> None:
    headers = sign_in(client, f"glm-schema-{type(payload).__name__}-{len(str(payload))}@example.com")
    assert client.put("/api/v1/ai/glm/credential", headers=headers, json={"api_key": "glm-test-secret-value"}).status_code == 200
    mock_transport(client, lambda _request: httpx.Response(200, json=payload))

    response = client.get("/api/v1/ai/glm/models", headers=headers)
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "model_discovery_unavailable"
    assert response.json()["error"]["provider"] == "glm"
    assert "glm-test-secret-value" not in response.text


def test_glm_model_discovery_reports_documentation_host_failure(client: TestClient) -> None:
    headers = sign_in(client, "glm-schema-network@example.com")
    assert client.put("/api/v1/ai/glm/credential", headers=headers, json={"api_key": "glm-test-secret-value"}).status_code == 200
    mock_transport(client, lambda _request: httpx.Response(503, text="Unavailable"))

    response = client.get("/api/v1/ai/glm/models", headers=headers)
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "model_discovery_unavailable"
    assert response.json()["error"]["provider"] == "glm"


def test_glm_chat_uses_documented_bearer_chat_endpoint_without_model_discovery(client: TestClient) -> None:
    headers = sign_in(client)
    assert client.put("/api/v1/ai/glm/credential", headers=headers, json={"api_key": "glm-test-secret-value"}).status_code == 200
    calls: list[httpx.Request] = []

    def handler(request: httpx.Request):
        calls.append(request)
        assert str(request.url) == "https://api.z.ai/api/paas/v4/chat/completions"
        assert request.method == "POST"
        assert request.headers["Authorization"] == "Bearer glm-test-secret-value"
        sent = __import__("json").loads(request.content)
        assert sent["model"] == "glm-account-model"
        assert sent["messages"] == [
            {"role": "system", "content": "Answer briefly."},
            {"role": "user", "content": "Hello"},
        ]
        return httpx.Response(200, json={"id": "chat-1", "model": "glm-account-model", "choices": [
            {"index": 0, "message": {"role": "assistant", "content": "Hi"}, "finish_reason": "stop"},
        ]})

    mock_transport(client, handler)
    response = client.post("/api/v1/ai/glm/chat", headers=headers, json={
        "model_id": "glm-account-model",
        "system_instruction": "Answer briefly.",
        "messages": [{"role": "user", "content": "Hello"}],
    })
    assert response.status_code == 200, response.text
    assert response.json()["choices"][0]["message"]["content"] == "Hi"
    assert len(calls) == 1


def test_glm_invalid_key_and_model_errors_are_classified_without_echoing_key(client: TestClient) -> None:
    headers = sign_in(client)
    secret = "glm-test-secret-value"
    assert client.put("/api/v1/ai/glm/credential", headers=headers, json={"api_key": secret}).status_code == 200
    mock_transport(client, lambda _request: httpx.Response(401, json={"error": {"message": "invalid key"}}))
    invalid = client.post("/api/v1/ai/glm/chat", headers=headers, json={
        "model_id": "glm-account-model", "messages": [{"role": "user", "content": "hello"}],
    })
    assert invalid.status_code == 401
    assert invalid.json()["error"]["code"] == "invalid_api_key"
    assert invalid.json()["error"]["provider"] == "glm"
    assert secret not in invalid.text

    from app.ai_proxy import classify_upstream_error
    unavailable = classify_upstream_error("glm", 400, {"error": {"message": "model does not exist"}}, "chat")
    assert unavailable.code == "model_unavailable"
    assert unavailable.provider == "glm"


@pytest.mark.parametrize("provider", ["groq", "glm"])
@pytest.mark.parametrize("payload", [
    {},
    {"choices": []},
    {"choices": [{"message": {"content": None}}]},
])
def test_chat_compatible_providers_reject_malformed_success_responses(client: TestClient, provider: str, payload: dict) -> None:
    headers = sign_in(client, f"malformed-chat-{provider}-{len(str(payload))}@example.com")
    secret = f"{provider}-test-secret-value"
    assert client.put(f"/api/v1/ai/{provider}/credential", headers=headers, json={"api_key": secret}).status_code == 200

    def handler(request: httpx.Request):
        if provider == "groq" and request.method == "GET":
            return httpx.Response(200, json={"data": [{"id": "malformed-test-model", "active": True}]})
        if provider == "glm":
            assert request.url.host == "api.z.ai"
            assert request.headers["Authorization"] == f"Bearer {secret}"
        return httpx.Response(200, json=payload)

    mock_transport(client, handler)
    response = client.post(f"/api/v1/ai/{provider}/chat", headers=headers, json={
        "model_id": "malformed-test-model",
        "messages": [{"role": "user", "content": "Hello"}],
    })
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "malformed_response"
    assert response.json()["error"]["provider"] == provider
    assert secret not in response.text


def test_network_failure_has_provider_specific_message(client: TestClient) -> None:
    headers = sign_in(client)
    assert save_groq_key(client, headers).status_code == 200

    def fail(_request: httpx.Request):
        raise httpx.ConnectError("connection refused")

    mock_transport(client, fail)
    response = client.get("/api/v1/ai/groq/models", headers=headers)
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "network_error"
    assert response.json()["error"]["message"] == "Unable to connect to Groq. Check your network connection."


def _ai_client_with_allowlists(free_models: str, paid_models: str) -> TestClient:
    app = create_app(Settings(
        app_env="test",
        database_url="sqlite://",
        jwt_secret="test-only-secret-with-adequate-length",
        auto_create_schema=True,
        ai_credentials_encryption_key=base64.urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode("ascii").rstrip("="),
        ai_verified_free_models=free_models,
        ai_verified_paid_models=paid_models,
    ))
    return TestClient(app)


def _provider_model_payload(provider: str, ids: list[str]) -> dict:
    if provider == "gemini":
        return {"models": [
            {"name": f"models/{model_id}-version-001", "baseModelId": model_id, "supportedGenerationMethods": ["generateContent"]}
            for model_id in ids
        ]}
    if provider == "glm":
        return {"components": {"schemas": {"ChatCompletionTextRequest": {
            "properties": {"model": {"type": "string", "enum": ids}},
        }}}}
    return {"data": [{"id": model_id, "active": True} for model_id in ids]}


def _provider_chat_payload(provider: str) -> dict:
    if provider == "gemini":
        return {"candidates": [{"content": {"parts": [{"text": "OK"}]}}]}
    return {"choices": [{"message": {"content": "OK"}}]}


@pytest.mark.parametrize("provider", ["gemini", "groq", "glm"])
def test_free_only_model_listing_filters_paid_unknown_and_conflicting_ids(provider: str) -> None:
    free = {provider: ["verified-free", "overlap"]}
    paid = {provider: ["verified-paid", "overlap"]}
    with _ai_client_with_allowlists(__import__("json").dumps(free), __import__("json").dumps(paid)) as client:
        headers = sign_in(client, f"listing-{provider}@example.com")
        assert client.put(f"/api/v1/ai/{provider}/credential", headers=headers, json={"api_key": "provider-test-secret"}).status_code == 200
        assert client.put("/api/v1/ai/settings/free-only", headers=headers, json={"enabled": True}).status_code == 200
        mock_transport(client, lambda _request: httpx.Response(200, json=_provider_model_payload(provider, [
            "verified-free", "verified-paid", "unknown-model", "overlap",
        ])))

        response = client.get(f"/api/v1/ai/{provider}/models", headers=headers)
        assert response.status_code == 200, response.text
        assert response.json()["data"] == [{"id": "verified-free", "pricing_status": "verified_free"}]


@pytest.mark.parametrize("provider", ["gemini", "groq", "glm"])
def test_free_only_rules_are_shared_by_chat_and_connection_test(provider: str) -> None:
    free = {provider: ["verified-free", "overlap"]}
    paid = {provider: ["verified-paid", "overlap"]}
    with _ai_client_with_allowlists(__import__("json").dumps(free), __import__("json").dumps(paid)) as client:
        headers = sign_in(client, f"chat-{provider}@example.com")
        assert client.put(f"/api/v1/ai/{provider}/credential", headers=headers, json={"api_key": "provider-test-secret"}).status_code == 200
        assert client.put("/api/v1/ai/settings/free-only", headers=headers, json={"enabled": True}).status_code == 200
        calls: list[str] = []

        def handler(request: httpx.Request):
            calls.append(str(request.url))
            if request.url.path.endswith("/models"):
                return httpx.Response(200, json=_provider_model_payload(provider, [
                    "verified-free", "verified-paid", "unknown-model", "overlap",
                ]))
            return httpx.Response(200, json=_provider_chat_payload(provider))

        mock_transport(client, handler)
        for model_id in ("verified-paid", "unknown-model", "overlap"):
            body = {"model_id": model_id, "messages": [{"role": "user", "content": "test"}]}
            assert client.post(f"/api/v1/ai/{provider}/test", headers=headers, json=body).json()["error"]["code"] == "model_not_verified_free"
            assert client.post(f"/api/v1/ai/{provider}/chat", headers=headers, json=body).json()["error"]["code"] == "model_not_verified_free"

        allowed = {"model_id": "verified-free", "messages": [{"role": "user", "content": "test"}]}
        assert client.post(f"/api/v1/ai/{provider}/test", headers=headers, json=allowed).status_code == 200
        assert client.post(f"/api/v1/ai/{provider}/chat", headers=headers, json=allowed).status_code == 200
        # Each denied request is rejected before any provider request is sent.
        assert len(calls) == (2 if provider == "glm" else 4)


@pytest.mark.parametrize("provider", ["gemini", "groq", "glm"])
def test_empty_free_allowlist_hides_every_model_and_blocks_calls(provider: str) -> None:
    with _ai_client_with_allowlists("{}", "{}") as client:
        headers = sign_in(client, f"empty-{provider}@example.com")
        assert client.put(f"/api/v1/ai/{provider}/credential", headers=headers, json={"api_key": "provider-test-secret"}).status_code == 200
        assert client.put("/api/v1/ai/settings/free-only", headers=headers, json={"enabled": True}).status_code == 200
        mock_transport(client, lambda _request: httpx.Response(200, json=_provider_model_payload(provider, ["provider-model"])))
        model_list = client.get(f"/api/v1/ai/{provider}/models", headers=headers)
        assert model_list.status_code == 200
        assert model_list.json()["data"] == []
        body = {"model_id": "provider-model", "messages": [{"role": "user", "content": "test"}]}
        assert client.post(f"/api/v1/ai/{provider}/test", headers=headers, json=body).json()["error"]["code"] == "model_not_verified_free"
        assert client.post(f"/api/v1/ai/{provider}/chat", headers=headers, json=body).json()["error"]["code"] == "model_not_verified_free"


@pytest.mark.parametrize("raw_free", [
    "not-json",
    '{"groq":"not-an-array"}',
    '{"other-provider":["model"]}',
    '{"groq":["   "]}',
    '{"groq":[42]}',
])
@pytest.mark.parametrize("route", ["models", "test", "chat"])
def test_malformed_free_allowlist_fails_closed(raw_free: str, route: str) -> None:
    with _ai_client_with_allowlists(raw_free, "{}") as client:
        headers = sign_in(client, f"malformed-{route}-{len(raw_free)}@example.com")
        assert save_groq_key(client, headers).status_code == 200
        assert client.put("/api/v1/ai/settings/free-only", headers=headers, json={"enabled": True}).status_code == 200
        calls: list[httpx.Request] = []

        def handler(request: httpx.Request):
            calls.append(request)
            return httpx.Response(200, json={"data": [{"id": "model"}], "choices": [{"message": {"content": "OK"}}]})

        mock_transport(client, handler)
        if route == "models":
            response = client.get("/api/v1/ai/groq/models", headers=headers)
        else:
            response = client.post(f"/api/v1/ai/groq/{route}", headers=headers, json={
                "model_id": "model", "messages": [{"role": "user", "content": "test"}],
            })
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "model_eligibility_unavailable"
        assert calls == []
        assert raw_free not in response.text


@pytest.mark.parametrize("provider", ["groq", "glm"])
def test_chat_completion_compatible_providers_accept_text_attachments(provider: str) -> None:
    with _ai_client_with_allowlists("{}", "{}") as client:
        headers = sign_in(client, f"text-attachment-{provider}@example.com")
        assert client.put(f"/api/v1/ai/{provider}/credential", headers=headers, json={"api_key": "provider-test-secret"}).status_code == 200
        observed_payloads: list[dict] = []

        def handler(request: httpx.Request):
            if request.url.path.endswith("/models"):
                return httpx.Response(200, json={"data": [{"id": "model", "active": True}]})
            observed_payloads.append(__import__("json").loads(request.content))
            return httpx.Response(200, json=_provider_chat_payload(provider))

        mock_transport(client, handler)
        response = client.post(f"/api/v1/ai/{provider}/chat", headers=headers, json={
            "model_id": "model",
            "messages": [{"role": "user", "content": "Summarize this", "attachments": [{"name": "note.txt", "text": "Source text"}]}],
        })
        assert response.status_code == 200, response.text
        assert "Attached file note.txt:\nSource text" in observed_payloads[0]["messages"][1]["content"]


@pytest.mark.parametrize("provider", ["groq", "glm"])
def test_binary_attachments_for_groq_and_glm_return_actionable_422(provider: str) -> None:
    with _ai_client_with_allowlists("{}", "{}") as client:
        headers = sign_in(client, f"binary-attachment-{provider}@example.com")
        assert client.put(f"/api/v1/ai/{provider}/credential", headers=headers, json={"api_key": "provider-test-secret"}).status_code == 200
        calls: list[httpx.Request] = []
        mock_transport(client, lambda request: (calls.append(request), httpx.Response(200, json={"data": [{"id": "model"}]}))[1])
        response = client.post(f"/api/v1/ai/{provider}/chat", headers=headers, json={
            "model_id": "model",
            "messages": [{"role": "user", "content": "Summarize this", "attachments": [{"name": "scan.pdf", "mime_type": "application/pdf", "data_base64": "ZmFrZQ=="}]}],
        })
        assert response.status_code == 422
        assert response.json()["error"]["code"] == "invalid_chat_request"
        assert "text attachments only" in response.json()["error"]["message"]
        assert "provider-test-secret" not in response.text
        assert calls == []


@pytest.mark.parametrize("mime_type", ["image/jpeg", "application/pdf"])
def test_gemini_binary_attachments_remain_supported(mime_type: str) -> None:
    with _ai_client_with_allowlists("{}", "{}") as client:
        headers = sign_in(client, f"gemini-binary-{mime_type.replace('/', '-')}@example.com")
        assert client.put("/api/v1/ai/gemini/credential", headers=headers, json={"api_key": "provider-test-secret"}).status_code == 200
        observed_payloads: list[dict] = []

        def handler(request: httpx.Request):
            if request.method == "GET":
                return httpx.Response(200, json={"models": [{
                    "name": "models/versioned-model-001", "baseModelId": "model",
                    "supportedGenerationMethods": ["generateContent"],
                }]})
            observed_payloads.append(__import__("json").loads(request.content))
            return httpx.Response(200, json=_provider_chat_payload("gemini"))

        mock_transport(client, handler)
        response = client.post("/api/v1/ai/gemini/chat", headers=headers, json={
            "model_id": "model",
            "messages": [{"role": "user", "content": "Describe this", "attachments": [{"name": "file", "mime_type": mime_type, "data_base64": "ZmFrZQ=="}]}],
        })
        assert response.status_code == 200, response.text
        assert observed_payloads[0]["contents"][0]["parts"][1]["inlineData"]["mimeType"] == mime_type


def test_credential_status_indicates_when_saved_key_cannot_be_decrypted(client: TestClient) -> None:
    headers = sign_in(client, "credential-status@example.com")
    assert save_groq_key(client, headers).status_code == 200
    object.__setattr__(client.app.state.settings, "ai_credentials_encryption_key", "")
    response = client.get("/api/v1/ai/groq/credential", headers=headers)
    assert response.status_code == 200
    assert response.json()["configured"] is False
    assert response.json()["saved"] is True
    assert response.json()["credential_usable"] is False
    assert "gsk-test-secret-value" not in response.text


def test_missing_encryption_key_does_not_block_auth_but_rejects_provider_key_storage() -> None:
    app = create_app(Settings(
        app_env="test",
        database_url="sqlite://",
        jwt_secret="test-only-secret-with-adequate-length",
        auto_create_schema=True,
        ai_credentials_encryption_key="",
    ))
    with TestClient(app) as unconfigured_client:
        headers = sign_in(unconfigured_client, "missing-vault@example.com")
        response = save_groq_key(unconfigured_client, headers)
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "credential_vault_unavailable"
        assert "gsk-test-secret-value" not in response.text
        status = unconfigured_client.get("/api/v1/ai/groq/credential", headers=headers)
        assert status.status_code == 200
        assert status.json()["configured"] is False
        assert status.json()["saved"] is False


def test_provider_credentials_are_scoped_to_each_authenticated_user(client: TestClient) -> None:
    first_user = sign_in(client, "credential-owner@example.com")
    second_user = sign_in(client, "credential-other@example.com")
    assert save_groq_key(client, first_user).status_code == 200

    other_status = client.get("/api/v1/ai/groq/credential", headers=second_user)
    assert other_status.status_code == 200
    assert other_status.json()["configured"] is False
    assert other_status.json()["saved"] is False
    assert client.delete("/api/v1/ai/groq/credential", headers=second_user).json()["configured"] is False
    assert client.get("/api/v1/ai/groq/credential", headers=first_user).json()["configured"] is True


@pytest.mark.parametrize("path,method", [
    ("/api/v1/ai/groq/credential", "get"),
    ("/api/v1/ai/groq/test", "post"),
    ("/api/v1/ai/groq/chat", "post"),
])
def test_credential_and_provider_calls_require_authenticated_session(client: TestClient, path: str, method: str) -> None:
    if method == "get":
        response = client.get(path)
    else:
        response = client.post(path, json={"model_id": "model", "messages": [{"role": "user", "content": "hello"}]})
    assert response.status_code == 401
