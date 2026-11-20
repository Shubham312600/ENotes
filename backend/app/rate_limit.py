from __future__ import annotations

import threading
import time
from collections import defaultdict, deque
from ipaddress import ip_address

from fastapi import HTTPException, Request


class RateLimiter:
    """Small per-process limiter for development and single-instance deployments."""

    def __init__(self) -> None:
        self._events: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def check(self, key: str, limit: int, window_seconds: int, now: float | None = None) -> None:
        timestamp = time.monotonic() if now is None else now
        with self._lock:
            events = self._events[key]
            while events and events[0] <= timestamp - window_seconds:
                events.popleft()
            if len(events) >= limit:
                wait = max(1, int(window_seconds - (timestamp - events[0])))
                raise HTTPException(status_code=429, detail={"code": "rate_limited", "message": "Too many attempts. Please wait and try again."}, headers={"Retry-After": str(wait)})
            events.append(timestamp)


def limit_request(request: Request, action: str, limit: int, window_seconds: int) -> None:
    if request.app.state.settings.app_env == "test":
        return
    client = rate_limit_client_identity(request)
    request.app.state.rate_limiter.check(f"{action}:{client}", limit, window_seconds)


def rate_limit_client_identity(request: Request) -> str:
    """Use Render/Cloudflare's edge-generated single-IP header in production.

    Uvicorn's configured proxy middleware trusts forwarded headers for the
    public Render proxy. Do not use its rewritten client address for limits:
    arbitrary leftmost X-Forwarded-For values can be supplied by a caller.
    Render routes public traffic through Cloudflare, which supplies
    CF-Connecting-IP. If it is absent or invalid, share a fail-closed bucket.
    """
    if request.app.state.settings.app_env == "production":
        values = request.headers.getlist("cf-connecting-ip")
        if len(values) == 1:
            try:
                return ip_address(values[0].strip()).compressed
            except ValueError:
                pass
        return "unverified-proxy-client"

    return request.client.host if request.client else "unknown"
