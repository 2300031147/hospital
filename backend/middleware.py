"""
AEROVHYN — Request Middleware
Logging, error handling, request timing, security headers, and request ID injection.
"""

import time
import traceback
import uuid
from datetime import datetime
from fastapi import Request, Response
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from logger import get_logger

log = get_logger("aerovhyn.http")

# Paths excluded from access logging (too noisy)
_SILENT_PATHS = frozenset({"/api/health", "/ws/updates"})


class RequestIDMiddleware(BaseHTTPMiddleware):
    """
    Attach a UUID request ID to every request.
    - Reads X-Request-ID from the client if present (allows frontend tracing).
    - Falls back to a server-generated UUID v4.
    - Echoes the final ID back in the response X-Request-ID header.
    """

    async def dispatch(self, request: Request, call_next):
        request_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
        # Store on request state so route handlers can read it
        request.state.request_id = request_id

        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        return response


class RequestTimingMiddleware(BaseHTTPMiddleware):
    """Add X-Response-Time header."""

    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()

        try:
            response = await call_next(request)
        except Exception as exc:
            elapsed = (time.perf_counter() - start) * 1000
            rid = getattr(request.state, "request_id", None)
            log.error(
                f"Unhandled exception in {request.method} {request.url.path}",
                extra={"request_id": rid, "elapsed_ms": round(elapsed, 1), "error": str(exc)},
            )
            return JSONResponse(
                status_code=500,
                content={"detail": "Internal server error", "type": type(exc).__name__},
            )

        elapsed = (time.perf_counter() - start) * 1000
        response.headers["X-Response-Time"] = f"{elapsed:.1f}ms"

        if elapsed > 500:
            rid = getattr(request.state, "request_id", None)
            log.warning(
                f"Slow request: {request.method} {request.url.path}",
                extra={"request_id": rid, "elapsed_ms": round(elapsed, 1)},
            )

        return response


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    """Log every request: method, path, status code, duration, request ID."""

    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        rid = getattr(request.state, "request_id", None)

        try:
            response = await call_next(request)
        except Exception as exc:
            elapsed = (time.perf_counter() - start) * 1000
            log.error(
                f"{request.method} {request.url.path} -> 500",
                extra={"request_id": rid, "elapsed_ms": round(elapsed, 1), "error": str(exc)},
            )
            raise

        elapsed = (time.perf_counter() - start) * 1000
        path = request.url.path

        if path not in _SILENT_PATHS and not path.startswith("/static"):
            status = response.status_code
            level = log.info if status < 400 else (log.warning if status < 500 else log.error)
            level(
                f"{request.method} {path} -> {status}",
                extra={"request_id": rid, "status": status, "elapsed_ms": round(elapsed, 1)},
            )

        return response


class GlobalExceptionMiddleware(BaseHTTPMiddleware):
    """Catch unhandled exceptions and return clean JSON with request ID."""

    async def dispatch(self, request: Request, call_next):
        try:
            return await call_next(request)
        except Exception as exc:
            rid = getattr(request.state, "request_id", None)
            log.error(
                "Uncaught exception",
                extra={
                    "request_id": rid,
                    "error": str(exc),
                    "traceback": traceback.format_exc(),
                },
            )
            return JSONResponse(
                status_code=500,
                content={
                    "detail": "An unexpected error occurred. Please try again.",
                    "error_type": type(exc).__name__,
                    "request_id": rid,
                },
            )


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    """Add modern security headers to all responses."""

    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["X-XSS-Protection"] = "1; mode=block"
        response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
        response.headers["Permissions-Policy"] = "geolocation=(), microphone=()"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; "
            "script-src 'self'; "
            "style-src 'self' 'unsafe-inline'; "
            "img-src 'self' data: https:; "
            "font-src 'self'; "
            "connect-src 'self'; "
            "frame-ancestors 'none'"
        )
        if request.url.scheme == "https":
            response.headers["Strict-Transport-Security"] = (
                "max-age=31536000; includeSubDomains"
            )
        return response
