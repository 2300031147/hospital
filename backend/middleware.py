"""
AEROVHYN — Request Middleware
Logging, error handling, and request timing for all API requests.
"""

import time
import traceback
from datetime import datetime
from fastapi import Request, Response
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware


class RequestTimingMiddleware(BaseHTTPMiddleware):
    """Add X-Response-Time header and log slow requests."""

    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        
        try:
            response = await call_next(request)
        except Exception as exc:
            elapsed = (time.perf_counter() - start) * 1000
            print(f"[ERROR] {request.method} {request.url.path} — {elapsed:.1f}ms — {exc}")
            return JSONResponse(
                status_code=500,
                content={"detail": "Internal server error", "type": type(exc).__name__},
            )

        elapsed = (time.perf_counter() - start) * 1000
        response.headers["X-Response-Time"] = f"{elapsed:.1f}ms"

        # Log slow requests (> 500ms)
        if elapsed > 500:
            print(f"[SLOW] {request.method} {request.url.path} — {elapsed:.1f}ms")

        return response


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    """Log all incoming requests with method, path, status, and timing."""

    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        
        try:
            response = await call_next(request)
        except Exception as exc:
            elapsed = (time.perf_counter() - start) * 1000
            print(f"[{datetime.utcnow().strftime('%H:%M:%S')}] {request.method} {request.url.path} → 500 ({elapsed:.0f}ms) — {exc}")
            raise

        elapsed = (time.perf_counter() - start) * 1000
        
        # Skip logging for WebSocket upgrades and health checks
        path = request.url.path
        if path not in ("/api/health", "/ws/updates") and not path.startswith("/static"):
            status = response.status_code
            level = "INFO" if status < 400 else ("WARN" if status < 500 else "ERR ")
            print(f"[{datetime.utcnow().strftime('%H:%M:%S')}] [{level}] {request.method} {path} → {status} ({elapsed:.0f}ms)")

        return response


class GlobalExceptionMiddleware(BaseHTTPMiddleware):
    """Catch unhandled exceptions and return clean JSON errors."""

    async def dispatch(self, request: Request, call_next):
        try:
            return await call_next(request)
        except Exception as exc:
            traceback.print_exc()
            return JSONResponse(
                status_code=500,
                content={
                    "detail": "An unexpected error occurred. Please try again.",
                    "error_type": type(exc).__name__,
                },
            )
