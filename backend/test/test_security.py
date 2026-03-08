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
