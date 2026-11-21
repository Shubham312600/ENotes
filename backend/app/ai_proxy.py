from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any
from urllib.parse import quote

import httpx

from .config import Settings


PROVIDERS = {"groq", "gemini", "glm"}
PROVIDER_LABELS = {"groq": "Groq", "gemini": "Gemini", "glm": "GLM"}
GEMINI_MODELS_MAX_PAGES = 20
GLM_MODEL_CATALOG_URL = "https://docs.z.ai/openapi.json"
# Uvicorn configures this INFO-level logger with its stderr handler in Render.
# The app.ai_proxy logger inherits Python's WARNING root level there, hiding INFO.
logger = logging.getLogger("uvicorn.error")


class _RedactPageTokenFilter(logging.Filter):
    _page_token_pattern = re.compile(r"([?&]pageToken=)[^&\s\"']*", re.IGNORECASE)

    def filter(self, record: logging.LogRecord) -> bool:
        message = record.getMessage()
        redacted = self._page_token_pattern.sub(r"\1[REDACTED]", message)
        if redacted != message:
            record.msg = redacted
            record.args = ()
        return True


# HTTPX logs request URLs at INFO level, which includes query parameters. Keep
# opaque Google pagination tokens out of those logs even when HTTPX INFO is on.
_httpx_logger = logging.getLogger("httpx")
if not any(isinstance(item, _RedactPageTokenFilter) for item in _httpx_logger.filters):
    _httpx_logger.addFilter(_RedactPageTokenFilter())

BASE_URLS = {
    "groq": "https://api.groq.com/openai/v1",
    # Keep the API version in request paths below so it is included exactly once.
    "gemini": "https://generativelanguage.googleapis.com",
    "glm": "https://api.z.ai/api/paas/v4",
}


@dataclass
class ProviderApiError(Exception):
    provider: str
    code: str
    message: str
    status: int
    technical_details: str = ""


def _error_payload(response: httpx.Response) -> dict[str, Any]:
    try:
        payload = response.json()
        return payload if isinstance(payload, dict) else {}
    except (ValueError, json.JSONDecodeError):
        return {}


def _error_text(payload: dict[str, Any]) -> str:
    # Gemini can report invalid credentials as HTTP 400 with the actual reason
    # nested under error.details[].reason, rather than as a 401 or top-level code.
    values: list[str] = []

    def collect(value: Any, key: str = "") -> None:
        if isinstance(value, dict):
            for child_key, child in value.items():
                if child_key.lower() in {"code", "type", "message", "status", "reason"}:
                    collect(child, child_key)
                elif isinstance(child, (dict, list)):
                    collect(child, child_key)
        elif isinstance(value, list):
            for child in value:
                collect(child, key)
        elif value is not None and key.lower() in {"code", "type", "message", "status", "reason"}:
            values.append(str(value))

    collect(payload)
    return " ".join(values).lower()


def classify_upstream_error(provider: str, status: int, payload: dict[str, Any], operation: str) -> ProviderApiError:
    label = PROVIDER_LABELS[provider]
    text = _error_text(payload)
    technical = f"HTTP {status}"
    gemini_invalid_key = provider == "gemini" and status == 400 and any(
        marker in text for marker in ("api_key_invalid", "api key not valid", "invalid api key", "api key is invalid")
    )
    if status == 401 or gemini_invalid_key:
        message = {
            "groq": "Groq rejected your API key. Verify your key in the Groq Console.",
            "gemini": "Gemini rejected your API key. Verify it in Google AI Studio.",
            "glm": "GLM rejected your API key. Verify the key in your Z.AI account.",
        }[provider]
        return ProviderApiError(provider, "invalid_api_key", message, status, technical)
    if status == 403:
        if any(term in text for term in ("quota", "billing", "insufficient_quota", "credit balance", "usage limit", "payment")):
            return ProviderApiError(provider, "quota_exhausted", f"Your {label} usage limit has been reached.", status, technical)
        message = {
            "groq": "Groq denied access for this key or model. Check key and model access in the Groq Console.",
            "gemini": "Gemini denied this key or project. Check Google AI Studio for a blocked key, Gemini API restriction, or project access.",
            "glm": "Z.AI denied access for this key or model. Check the key and model access in your Z.AI account.",
        }[provider]
        return ProviderApiError(provider, "permission_denied", message, status, "HTTP 403: provider access is forbidden for this key or account.")
    if status == 429:
        if any(term in text for term in ("quota", "billing", "insufficient_quota", "credit balance", "usage limit", "payment")):
            return ProviderApiError(provider, "quota_exhausted", f"Your {label} usage limit has been reached.", status, technical)
        return ProviderApiError(provider, "rate_limited", f"{label} rate limit reached. Please retry later.", status, technical)
    model_unavailable_markers = ("model_not_found", "model unavailable", "model not found", "decommissioned model")
    if provider == "glm":
        model_unavailable_markers += ("model does not exist", "model not exist", "unknown model", "invalid model", "unsupported model", "model is not available")
    elif provider == "gemini":
        model_unavailable_markers += ("model does not exist", "model not exist", "unknown model", "invalid model", "unsupported model", "not found for api version", "not supported for generatecontent", "does not support generatecontent")
    if operation in {"chat", "test"} and (
        status == 404 or any(term in text for term in model_unavailable_markers)
    ):
        return ProviderApiError(provider, "model_unavailable", f"The selected {label} model is unavailable.", status, technical)
    return ProviderApiError(provider, "unknown_error", f"{label} connection failed. Check the technical error details.", status, technical)


def _headers(provider: str, api_key: str) -> dict[str, str]:
    if provider == "gemini":
        return {"x-goog-api-key": api_key}
    return {"Authorization": f"Bearer {api_key}"}


async def request_provider(
    provider: str,
    api_key: str,
    path: str,
    *,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    params: dict[str, str] | None = None,
    operation: str = "models",
    transport: httpx.AsyncBaseTransport | None = None,
) -> dict[str, Any]:
    if provider not in PROVIDERS:
        raise ValueError("Unsupported AI provider.")
    url = BASE_URLS[provider] + path
    headers = {**_headers(provider, api_key), "Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(40.0, connect=12.0), follow_redirects=False, transport=transport) as client:
            response = await client.request(method, url, headers=headers, json=payload, params=params)
    except httpx.TimeoutException as error:
        raise ProviderApiError(provider, "network_error", f"Unable to connect to {PROVIDER_LABELS[provider]}. Check your network connection.", 0, "Request timed out.") from error
    except httpx.HTTPError as error:
        raise ProviderApiError(provider, "network_error", f"Unable to connect to {PROVIDER_LABELS[provider]}. Check your network connection.", 0, "The secure provider request failed before an HTTP response was received.") from error
    if not response.is_success:
        raise classify_upstream_error(provider, response.status_code, _error_payload(response), operation)
    try:
        body = response.json()
    except (ValueError, json.JSONDecodeError) as error:
        raise ProviderApiError(provider, "unknown_error", f"{PROVIDER_LABELS[provider]} connection failed. Check the technical error details.", response.status_code, "Provider returned a non-JSON response.") from error
    if not isinstance(body, dict):
        raise ProviderApiError(provider, "unknown_error", f"{PROVIDER_LABELS[provider]} connection failed. Check the technical error details.", 502, "Provider returned an unexpected response shape.")
    return body


async def _request_glm_model_catalog(
    transport: httpx.AsyncBaseTransport | None = None,
) -> dict[str, Any]:
    """Load GLM model IDs from Z.AI's published API schema.

    Z.AI's official API specification documents model IDs in the request schema,
    but does not document an authenticated model-list endpoint. This request is
    to the documentation host and must never include a user's Z.AI credential.
    """
    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(40.0, connect=12.0),
            follow_redirects=False,
            transport=transport,
        ) as client:
            response = await client.get(GLM_MODEL_CATALOG_URL, headers={"Accept": "application/json"})
    except httpx.TimeoutException as error:
        raise ProviderApiError(
            "glm", "model_discovery_unavailable",
            "Z.AI's published model catalog could not be loaded. Check your network connection and retry.",
            502, "The official Z.AI API schema request timed out.",
        ) from error
    except httpx.HTTPError as error:
        raise ProviderApiError(
            "glm", "model_discovery_unavailable",
            "Z.AI's published model catalog could not be loaded. Check your network connection and retry.",
            502, "The official Z.AI API schema request failed before receiving a response.",
        ) from error

    if not response.is_success:
        raise ProviderApiError(
            "glm", "model_discovery_unavailable",
            "Z.AI's published model catalog could not be loaded. Retry later or check the Z.AI API documentation.",
            502, f"The official Z.AI API schema returned HTTP {response.status_code}.",
        )
    try:
        body = response.json()
    except (ValueError, json.JSONDecodeError) as error:
        raise ProviderApiError(
            "glm", "model_discovery_unavailable",
            "Z.AI returned an unreadable model catalog. Retry later.",
            502, "The official Z.AI API schema was not valid JSON.",
        ) from error
    if not isinstance(body, dict):
        raise ProviderApiError(
            "glm", "model_discovery_unavailable",
            "Z.AI returned an unexpected model catalog. Retry later.",
            502, "The official Z.AI API schema root was not an object.",
        )
    return body


def _parse_verified_model_ids(raw: str, setting_name: str) -> dict[str, set[str]]:
    try:
        value = json.loads(raw or "{}")
    except (ValueError, TypeError) as error:
        raise ValueError(f"{setting_name} must be a JSON object of provider IDs to model ID arrays.") from error
    if not isinstance(value, dict):
        raise ValueError(f"{setting_name} must be a JSON object of provider IDs to model ID arrays.")

    parsed = {provider: set() for provider in PROVIDERS}
    for provider, model_ids in value.items():
        if provider not in PROVIDERS or not isinstance(model_ids, list):
            raise ValueError(f"{setting_name} contains an unsupported provider or malformed model list.")
        for model_id in model_ids:
            if not isinstance(model_id, str) or not model_id or model_id != model_id.strip() or len(model_id) > 200:
                raise ValueError(f"{setting_name} contains an invalid model ID.")
            parsed[provider].add(model_id)
    return parsed


def _verified_model_ids(settings: Settings, provider: str) -> tuple[set[str], set[str]]:
    try:
        free = _parse_verified_model_ids(settings.ai_verified_free_models, "AI_VERIFIED_FREE_MODELS")
        paid = _parse_verified_model_ids(settings.ai_verified_paid_models, "AI_VERIFIED_PAID_MODELS")
    except ValueError as error:
        # Do not include raw environment values in an error response.
        raise ProviderApiError(
            provider,
            "model_eligibility_unavailable",
            f"{PROVIDER_LABELS[provider]} model eligibility is not configured correctly. Contact the service operator.",
            503,
            "The server-side model eligibility configuration is malformed.",
        ) from error
    return free[provider], paid[provider]


def model_pricing_status(settings: Settings, provider: str, model_id: str, *, strict: bool = False) -> str:
    try:
        free, paid = _verified_model_ids(settings, provider)
    except ProviderApiError:
        if strict:
            raise
        return "unknown"
    if model_id in free and model_id in paid:
        return "unknown"
    if model_id in free:
        return "verified_free"
    if model_id in paid:
        return "paid"
    return "unknown"


def require_verified_free_model(settings: Settings, provider: str, model_id: str) -> None:
    status = model_pricing_status(settings, provider, model_id, strict=True)
    if status != "verified_free":
        raise ProviderApiError(
            provider,
            "model_not_verified_free",
            f"The selected {PROVIDER_LABELS[provider]} model is not verified as free for this service.",
            403,
            "Free-only mode allows only explicitly verified free model IDs.",
        )


async def _gemini_model_records(
    api_key: str,
    transport: httpx.AsyncBaseTransport | None,
) -> tuple[list[Any], int, str | None, ProviderApiError | None]:
    """Fetch bounded pages from Google's model catalog without exposing page tokens."""
    records: list[Any] = []
    requested_tokens: set[str] = set()
    page_token: str | None = None
    pages_retrieved = 0

    while pages_retrieved < GEMINI_MODELS_MAX_PAGES:
        params = {"pageToken": page_token} if page_token is not None else None
        if page_token is not None:
            requested_tokens.add(page_token)
        try:
            root = await request_provider(
                "gemini", api_key, "/v1beta/models", params=params, transport=transport,
            )
        except ProviderApiError as error:
            return records, pages_retrieved, None, error
        pages_retrieved += 1
        page_records = root.get("models")
        if not isinstance(page_records, list):
            return records, pages_retrieved, None, ProviderApiError(
                "gemini", "unknown_error",
                "Gemini connection failed. Check the technical error details.",
                502, "Model-list response did not contain the documented model array.",
            )
        records.extend(page_records)

        next_page_token = root.get("nextPageToken")
        if next_page_token is None or next_page_token == "":
            return records, pages_retrieved, None, None
        if not isinstance(next_page_token, str):
            return records, pages_retrieved, "malformed_page_token", None
        if next_page_token in requested_tokens:
            return records, pages_retrieved, "repeated_page_token", None
        if pages_retrieved >= GEMINI_MODELS_MAX_PAGES:
            return records, pages_retrieved, "maximum_pages_reached", None
        page_token = next_page_token

    # The loop can only end this way if the configured limit is nonpositive.
    return records, pages_retrieved, "maximum_pages_reached", None


def _log_gemini_model_discovery(stats: dict[str, int | bool | str]) -> None:
    # Only fixed labels and counters are logged. Never include IDs, provider
    # payloads, credentials, or opaque pagination tokens in this event.
    logger.info(
        "Gemini model discovery diagnostics: %s",
        json.dumps({"event": "gemini_model_discovery", **stats}, separators=(",", ":"), sort_keys=True),
    )


async def list_provider_models(
    settings: Settings,
    provider: str,
    api_key: str,
    transport: httpx.AsyncBaseTransport | None = None,
    *,
    free_only: bool = False,
) -> list[dict[str, str]]:
    if provider not in PROVIDERS:
        raise ValueError("Unsupported AI provider.")
    # Validate before making an upstream call when Free-only is enabled.
    if free_only:
        _verified_model_ids(settings, provider)
    if provider == "glm":
        root = await _request_glm_model_catalog(transport)
        try:
            values = root["components"]["schemas"]["ChatCompletionTextRequest"]["properties"]["model"]["enum"]
        except (KeyError, TypeError):
            raise ProviderApiError(
                provider, "model_discovery_unavailable",
                "Z.AI's published model catalog has an unexpected format. Retry later.",
                502, "The official API schema did not include the documented chat model enum.",
            )
        if not isinstance(values, list) or any(not isinstance(item, str) for item in values):
            raise ProviderApiError(
                provider, "model_discovery_unavailable",
                "Z.AI's published model catalog has an unexpected format. Retry later.",
                502, "The official API schema chat model enum was malformed.",
            )
    elif provider == "gemini":
        values, pages_retrieved, pagination_issue, deferred_error = await _gemini_model_records(api_key, transport)
    else:
        root = await request_provider(provider, api_key, "/models", transport=transport)
        values = root.get("data")
    if not isinstance(values, list):
        raise ProviderApiError(provider, "unknown_error", f"{PROVIDER_LABELS[provider]} connection failed. Check the technical error details.", 502, "Model-list response did not contain the documented model array.")
    models: list[dict[str, str]] = []
    seen: set[str] = set()
    gemini_stats: dict[str, int | bool | str] | None = None
    if provider == "gemini":
        gemini_stats = {
            "provider": "gemini",
            "free_only": free_only,
            "outcome": "upstream_error" if deferred_error is not None else "pagination_error" if pagination_issue is not None else "success",
            "upstream_records_received": len(values),
            "valid_base_model_id_records": 0,
            "valid_model_records": 0,
            "records_supporting_generate_content": 0,
            "invalid_model_records": 0,
            "rejected_unsupported_or_malformed_records": 0,
            "duplicate_model_records": 0,
            "verified_free_models": 0,
            "unknown_pricing_models": 0,
            "paid_models": 0,
            "pricing_filtered_unknown": 0,
            "pricing_filtered_paid": 0,
            "pages_retrieved": pages_retrieved,
            "final_models_returned": 0,
        }
    for item in values:
        if provider == "glm":
            model_id = item.strip() if isinstance(item, str) else ""
        elif not isinstance(item, dict):
            if gemini_stats is not None:
                gemini_stats["invalid_model_records"] += 1
                gemini_stats["rejected_unsupported_or_malformed_records"] += 1
            continue
        elif provider == "gemini":
            # `name` is a versioned resource name. Google documents
            # `baseModelId` as the identifier to pass to generateContent.
            raw_model_id = item.get("baseModelId")
            model_id = raw_model_id.strip() if isinstance(raw_model_id, str) else ""
        else:
            raw_model_id = item.get("id")
            model_id = raw_model_id.strip() if isinstance(raw_model_id, str) else ""
        valid_model_id = bool(model_id) and model_id != "null" and len(model_id) <= 200
        supports_generation = True
        if provider == "gemini":
            methods = item.get("supportedGenerationMethods")
            supports_generation = isinstance(methods, list) and "generateContent" in methods
            assert gemini_stats is not None
            if valid_model_id:
                gemini_stats["valid_base_model_id_records"] += 1
            if supports_generation:
                gemini_stats["records_supporting_generate_content"] += 1
            if not valid_model_id or not supports_generation:
                gemini_stats["invalid_model_records"] += 1
                gemini_stats["rejected_unsupported_or_malformed_records"] += 1
                continue
            gemini_stats["valid_model_records"] += 1
        if not valid_model_id:
            continue
        if model_id in seen:
            if gemini_stats is not None:
                gemini_stats["duplicate_model_records"] += 1
            continue
        if provider == "groq" and item.get("active") is False:
            continue
        seen.add(model_id)
        pricing_status = model_pricing_status(settings, provider, model_id, strict=free_only)
        if gemini_stats is not None:
            if pricing_status == "verified_free":
                gemini_stats["verified_free_models"] += 1
            elif pricing_status == "paid":
                gemini_stats["paid_models"] += 1
            else:
                gemini_stats["unknown_pricing_models"] += 1
        if free_only and pricing_status != "verified_free":
            if gemini_stats is not None:
                field = "pricing_filtered_paid" if pricing_status == "paid" else "pricing_filtered_unknown"
                gemini_stats[field] += 1
            continue
        models.append({"id": model_id, "pricing_status": pricing_status})
    if gemini_stats is not None:
        gemini_stats["final_models_returned"] = (
            0 if deferred_error is not None or pagination_issue is not None else len(models)
        )
        _log_gemini_model_discovery(gemini_stats)
        if deferred_error is not None:
            raise deferred_error
        if pagination_issue is not None:
            reasons = {
                "malformed_page_token": "Google returned a malformed pagination token.",
                "repeated_page_token": "Google repeated a pagination token.",
                "maximum_pages_reached": "The model catalog exceeded the pagination limit.",
            }
            raise ProviderApiError(
                "gemini", "model_discovery_unavailable",
                "Gemini's model catalog could not be fully loaded. Retry later.",
                502, reasons[pagination_issue],
            )
    return sorted(models, key=lambda item: item["id"].casefold())


def _chat_completion_payload(body: dict[str, Any]) -> dict[str, Any]:
    model = body.get("model_id")
    messages = body.get("messages")
    if not isinstance(model, str) or not model.strip() or len(model) > 200:
        raise ValueError("Choose a model returned by the provider.")
    if not isinstance(messages, list) or not messages or len(messages) > 32:
        raise ValueError("The chat request has an invalid message list.")
    system = body.get("system_instruction", "")
    if not isinstance(system, str) or len(system) > 12_000:
        raise ValueError("The chat instructions are too long.")
    safe_messages: list[dict[str, str]] = []
    for item in messages:
        if not isinstance(item, dict) or item.get("role") not in {"user", "assistant"}:
            raise ValueError("The chat request contains an invalid message.")
        content = item.get("content", "")
        if not isinstance(content, str) or len(content) > 20_000:
            raise ValueError("The chat message is too long.")
        attachments = item.get("attachments", [])
        if not isinstance(attachments, list) or len(attachments) > 3:
            raise ValueError("The chat request contains too many attachments.")
        for attachment in attachments:
            if not isinstance(attachment, dict):
                raise ValueError("The chat request contains an invalid attachment.")
            text = attachment.get("text")
            if text is not None:
                if not isinstance(text, str) or len(text) > 128_000:
                    raise ValueError("An attachment is too large for this request.")
                content += f"\n\nAttached file {str(attachment.get('name', 'file'))[:180]}:\n{text}"
            elif attachment.get("data_base64") is not None:
                raise ValueError("Groq and GLM support text attachments only. Remove the binary attachment or use Gemini for supported images and PDFs.")
            else:
                raise ValueError("An attachment must include readable text or supported file data.")
        safe_messages.append({"role": item["role"], "content": content})
    return {"model": model.strip(), "messages": [{"role": "system", "content": system}, *safe_messages]}


def _gemini_chat_payload(body: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    model = body.get("model_id")
    messages = body.get("messages")
    system = body.get("system_instruction", "")
    if not isinstance(model, str) or not model.strip() or len(model) > 200:
        raise ValueError("Choose a model returned by the provider.")
    if not isinstance(messages, list) or not messages or len(messages) > 32:
        raise ValueError("The chat request has an invalid message list.")
    if not isinstance(system, str) or len(system) > 12_000:
        raise ValueError("The chat instructions are too long.")
    contents: list[dict[str, Any]] = []
    for item in messages:
        if not isinstance(item, dict) or item.get("role") not in {"user", "assistant"}:
            raise ValueError("The chat request contains an invalid message.")
        parts: list[dict[str, Any]] = []
        content = item.get("content", "")
        if not isinstance(content, str) or len(content) > 20_000:
            raise ValueError("The chat message is too long.")
        if content:
            parts.append({"text": content})
        attachments = item.get("attachments", [])
        if not isinstance(attachments, list) or len(attachments) > 3:
            raise ValueError("The chat request contains too many attachments.")
        for attachment in attachments:
            if not isinstance(attachment, dict):
                raise ValueError("The chat request contains an invalid attachment.")
            if isinstance(attachment.get("text"), str):
                parts.append({"text": f"Attached file {str(attachment.get('name', 'file'))[:180]}:\n{attachment['text'][:128_000]}"})
            elif attachment.get("text") is not None:
                raise ValueError("The attachment text is invalid.")
            elif isinstance(attachment.get("data_base64"), str):
                mime = attachment.get("mime_type")
                if not isinstance(mime, str) or mime not in {"image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"}:
                    raise ValueError("This attachment type is not supported by Gemini.")
                parts.append({"inlineData": {"mimeType": mime, "data": attachment["data_base64"]}})
            elif attachment.get("data_base64") is not None:
                raise ValueError("The attachment data is invalid.")
            else:
                raise ValueError("An attachment must include readable text or supported file data.")
        contents.append({"role": "model" if item["role"] == "assistant" else "user", "parts": parts or [{"text": " "}]})
    payload: dict[str, Any] = {"contents": contents}
    if system:
        payload["systemInstruction"] = {"parts": [{"text": system}]}
    return model.strip(), payload


async def chat_provider(
    settings: Settings,
    provider: str,
    api_key: str,
    body: dict[str, Any],
    *,
    free_only: bool,
    transport: httpx.AsyncBaseTransport | None = None,
) -> dict[str, Any]:
    if free_only:
        # Check the operator-owned allowlist before any provider model/chat call.
        require_verified_free_model(settings, provider, body.get("model_id", ""))
    if provider == "gemini":
        model_id, payload = _gemini_chat_payload(body)
    else:
        payload = _chat_completion_payload(body)
        model_id = payload["model"]
    if provider != "glm":
        try:
            models = await list_provider_models(settings, provider, api_key, transport)
        except ProviderApiError:
            raise
        available = {item["id"] for item in models}
        if model_id not in available:
            raise ProviderApiError(provider, "model_unavailable", f"The selected {PROVIDER_LABELS[provider]} model is unavailable.", 404, "The selected model ID is not present in the provider model list.")
    path = "/v1beta/models/" + quote(model_id, safe="") + ":generateContent" if provider == "gemini" else "/chat/completions"
    result = await request_provider(provider, api_key, path, method="POST", payload=payload, operation="chat", transport=transport)
    _validate_chat_response(provider, result)
    return result


def _validate_chat_response(provider: str, result: dict[str, Any]) -> None:
    """Reject malformed success bodies without echoing provider response data."""
    if provider == "gemini":
        candidates = result.get("candidates")
        if not isinstance(candidates, list) or not candidates:
            prompt_feedback = result.get("promptFeedback")
            block_reason = prompt_feedback.get("blockReason") if isinstance(prompt_feedback, dict) else None
            if isinstance(block_reason, str) and block_reason not in {"", "BLOCK_REASON_UNSPECIFIED"}:
                raise ProviderApiError(
                    provider,
                    "content_blocked",
                    "Gemini blocked this prompt. Rephrase it and try again.",
                    422,
                    "Gemini returned a prompt block reason without candidates.",
                )
            raise ProviderApiError(
                provider,
                "malformed_response",
                "Gemini returned an unexpected chat response. Try again or choose another model.",
                502,
                "Successful Gemini response did not contain a candidate list.",
            )
        first = candidates[0]
        if not isinstance(first, dict):
            valid_content = False
        else:
            content = first.get("content")
            parts = content.get("parts") if isinstance(content, dict) else None
            valid_content = isinstance(parts, list) and all(isinstance(part, dict) for part in parts)
            finish_reason = first.get("finishReason")
            if not valid_content and finish_reason in {"SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "IMAGE_SAFETY"}:
                raise ProviderApiError(
                    provider,
                    "content_blocked",
                    "Gemini blocked this prompt. Rephrase it and try again.",
                    422,
                    "Gemini blocked the candidate content.",
                )
        if not valid_content:
            raise ProviderApiError(
                provider,
                "malformed_response",
                "Gemini returned an unexpected chat response. Try again or choose another model.",
                502,
                "Successful Gemini response did not contain candidate content parts.",
            )
        return

    if provider in {"groq", "glm"}:
        choices = result.get("choices")
        first = choices[0] if isinstance(choices, list) and choices else None
        message = first.get("message") if isinstance(first, dict) else None
        content = message.get("content") if isinstance(message, dict) else None
        tool_calls = message.get("tool_calls") if isinstance(message, dict) else None
        if not isinstance(message, dict) or not (
            isinstance(content, (str, list)) or isinstance(tool_calls, list)
        ):
            label = PROVIDER_LABELS[provider]
            raise ProviderApiError(
                provider,
                "malformed_response",
                f"{label} returned an unexpected chat response. Try again or choose another model.",
                502,
                f"Successful {label} response did not contain a chat choice with message content.",
            )


async def test_provider_model(
    settings: Settings,
    provider: str,
    api_key: str,
    model_id: str,
    *,
    free_only: bool,
    transport: httpx.AsyncBaseTransport | None = None,
) -> None:
    result = await chat_provider(settings, provider, api_key, {
        "model_id": model_id,
        "system_instruction": "Reply only with the word OK.",
        "messages": [{"role": "user", "content": "Connection test. Reply OK."}],
    }, free_only=free_only, transport=transport)
    if provider == "gemini":
        candidates = result.get("candidates")
        first = candidates[0] if isinstance(candidates, list) and candidates else {}
        content = first.get("content", {}) if isinstance(first, dict) else {}
        parts = content.get("parts", []) if isinstance(content, dict) else []
        text = "".join(part.get("text", "") for part in parts if isinstance(part, dict)) if isinstance(parts, list) else ""
    else:
        choices = result.get("choices")
        first = choices[0] if isinstance(choices, list) and choices else {}
        message = first.get("message", {}) if isinstance(first, dict) else {}
        text = message.get("content", "") if isinstance(message, dict) else ""
    if not isinstance(text, str) or not text.strip():
        label = PROVIDER_LABELS[provider]
        raise ProviderApiError(provider, "empty_test_response", f"{label} connection failed. Check the technical error details.", 502, "The selected model returned no text for the connection test.")
