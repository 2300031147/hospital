import sys
import os

if "AEROVHYN_JWT_SECRET" not in os.environ:
    os.environ["AEROVHYN_JWT_SECRET"] = "temporary_secret_for_tests_" + ("1" * 32)
if "CORS_ORIGINS" not in os.environ:
    os.environ["CORS_ORIGINS"] = "http://localhost:5173"

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import pytest
from fastapi.testclient import TestClient
from main import app
import time

# Bug #60: Prevent stale test test_aerovhyn.db from contaminating subsequent CI runs
@pytest.fixture(autouse=True)
def setup_db():
    if os.path.exists("test_aerovhyn.db"):
        try:
            os.remove("test_aerovhyn.db")
        except PermissionError:
            time.sleep(0.1)
            try:
                os.remove("test_aerovhyn.db")
            except Exception:
                pass
    yield
    if os.path.exists("test_aerovhyn.db"):
        try:
            os.remove("test_aerovhyn.db")
        except Exception:
            pass

client = TestClient(app)

def test_cors_origin_rejected():
    response = client.options("/api/health", headers={
        "Origin": "http://evil.com",
        "Access-Control-Request-Method": "GET"
    })
    # If the origin is not allowed, FastAPI CORS middleware typically doesn't echo it back
    allowed_origin = response.headers.get("access-control-allow-origin")
    assert allowed_origin != "http://evil.com"

def test_cors_origin_allowed():
    response = client.options("/api/health", headers={
        "Origin": "http://localhost:5173",
        "Access-Control-Request-Method": "GET"
    })
    allowed_origin = response.headers.get("access-control-allow-origin")
    assert allowed_origin == "http://localhost:5173"

def test_reset_requires_auth():
    response = client.post("/api/simulate/reset")
    assert response.status_code == 401

def test_acknowledge_requires_auth():
    response = client.post("/api/hospitals/1/acknowledge")
    assert response.status_code == 401

def test_security_headers_present():
    response = client.get("/api/health")
    assert response.headers.get("x-content-type-options") == "nosniff"
    assert response.headers.get("x-frame-options") == "DENY"
    assert response.headers.get("x-xss-protection") == "1; mode=block"

def test_health_check_works():
    response = client.get("/api/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


# --- New Security Tests ---

def test_content_security_policy_header():
    """Verify Content-Security-Policy header is set on all responses."""
    response = client.get("/api/health")
    csp = response.headers.get("content-security-policy")
    assert csp is not None, "Content-Security-Policy header is missing"
    assert "default-src 'self'" in csp
    assert "frame-ancestors 'none'" in csp

def test_referrer_policy_header():
    """Verify Referrer-Policy header is set."""
    response = client.get("/api/health")
    assert response.headers.get("referrer-policy") == "strict-origin-when-cross-origin"

def test_permissions_policy_header():
    """Verify Permissions-Policy header restricts geolocation and microphone."""
    response = client.get("/api/health")
    pp = response.headers.get("permissions-policy")
    assert pp is not None
    assert "geolocation=()" in pp
    assert "microphone=()" in pp

def test_jwt_expiry_is_short():
    """Verify JWT token expiry is not excessively long (max 2 hours)."""
    from auth import ACCESS_TOKEN_EXPIRE_MINUTES
    assert ACCESS_TOKEN_EXPIRE_MINUTES <= 120, (
        f"JWT expiry is {ACCESS_TOKEN_EXPIRE_MINUTES} minutes — should be ≤ 120 minutes"
    )

def test_simulate_overload_requires_auth():
    """Verify simulation endpoints require authentication."""
    response = client.post("/api/simulate/overload/1")
    assert response.status_code == 401

def test_classify_endpoint_accessible():
    """Verify classify endpoint works for unauthenticated access (public health API)."""
    response = client.post("/api/classify", json={
        "heart_rate": 100,
        "spo2": 95,
        "systolic_bp": 120,
        "emergency_type": "general",
        "age": 30,
    })
    assert response.status_code == 200

def test_password_reset_model_enforces_strength():
    """Verify PasswordReset model validates password strength."""
    from pydantic import ValidationError
    from main import PasswordReset

    # Too short
    with pytest.raises(ValidationError):
        PasswordReset(new_password="Ab1")

    # No uppercase
    with pytest.raises(ValidationError):
        PasswordReset(new_password="abcdefg1")

    # No digit
    with pytest.raises(ValidationError):
        PasswordReset(new_password="Abcdefgh")

    # Valid password
    p = PasswordReset(new_password="SecureP4ss")
    assert p.new_password == "SecureP4ss"

def test_database_error_message_no_credentials():
    """Verify DATABASE_URL error message doesn't expose credential format."""
    from database import get_db
    import asyncio

    original = os.environ.get("DATABASE_URL")
    os.environ.pop("DATABASE_URL", None)

    # Need to reimport to pick up the change
    import database
    saved_url = database.DATABASE_URL
    database.DATABASE_URL = None

    try:
        with pytest.raises(RuntimeError) as exc_info:
            asyncio.get_event_loop().run_until_complete(get_db())
        error_msg = str(exc_info.value)
        assert "user:password" not in error_msg, "Error message exposes credential format"
        assert "postgresql://" not in error_msg, "Error message exposes connection string format"
    finally:
        database.DATABASE_URL = saved_url
        if original:
            os.environ["DATABASE_URL"] = original

def test_audit_log_uses_parameterized_queries():
    """Verify audit_log.py uses parameterized queries instead of f-strings for SQL."""
    import inspect
    from audit_log import add_block

    source = inspect.getsource(add_block)
    # Should NOT use f-string for advisory lock
    assert "f\"SELECT pg_advisory_lock" not in source, (
        "audit_log uses f-string SQL — must use parameterized queries"
    )
    assert "f\"SELECT pg_advisory_unlock" not in source, (
        "audit_log uses f-string SQL — must use parameterized queries"
    )
