import sys
import os
import pytest
from fastapi.testclient import TestClient

# Use a test database
os.environ["AEROVHYN_DB_PATH"] = "test_aerovhyn.db"
os.environ["AEROVHYN_JWT_SECRET"] = "temporary_secret_for_tests_" + ("1" * 32)
os.environ["CORS_ORIGINS"] = "http://localhost:5173"
# Ensure we don't try to use Postgres locally for tests
if "DATABASE_URL" in os.environ:
    del os.environ["DATABASE_URL"]

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from main import app
from database import init_db, seed_data

client = TestClient(app)

import asyncio

async def _setup():
    # Clear test db if it exists
    if os.path.exists("test_aerovhyn.db"):
        try: os.remove("test_aerovhyn.db")
        except: pass
    await init_db()
    await seed_data()

@pytest.fixture(autouse=True)
def setup_db():
    asyncio.run(_setup())
    yield
    # Cleanup
    if os.path.exists("test_aerovhyn.db"):
        try: os.remove("test_aerovhyn.db")
        except: pass

def test_login_success():
    # The seeded database has paramedic1 / rescue123
    with TestClient(app) as c:
        response = c.post("/api/auth/token", json={
            "username": "paramedic1",
            "password": "rescue123"
        }, headers={"X-Forwarded-For": "1.2.3.4"})
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"
        assert data["role"] == "paramedic"
        # login response returns full_name not username
        assert data["full_name"] is not None
        assert "ambulance_id" in data
        assert "hospital_id" in data

def test_login_invalid_password():
    with TestClient(app) as c:
        response = c.post("/api/auth/token", json={
            "username": "paramedic1",
            "password": "wrongpassword"
        }, headers={"X-Forwarded-For": "1.2.3.5"})
        assert response.status_code == 401
        assert response.json()["detail"] == "Invalid username or password"

def test_login_invalid_username():
    with TestClient(app) as c:
        response = c.post("/api/auth/token", json={
            "username": "unknownuser",
            "password": "somepassword"
        }, headers={"X-Forwarded-For": "1.2.3.6"})
        assert response.status_code == 401
