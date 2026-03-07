import sys
import os
import pytest
from fastapi.testclient import TestClient

# Use a test database
os.environ["AEROVHYN_DB_PATH"] = "test_aerovhyn.db"
os.environ["AEROVHYN_JWT_SECRET"] = "temporary_secret_for_tests_" + ("1" * 32)
os.environ["CORS_ORIGINS"] = "http://localhost:5173"
if "DATABASE_URL" in os.environ:
    del os.environ["DATABASE_URL"]

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from main import app
from database import init_db, seed_data

import asyncio

async def _setup():
    if os.path.exists("test_aerovhyn.db"):
        try: os.remove("test_aerovhyn.db")
        except: pass
    await init_db()
    await seed_data()

@pytest.fixture(autouse=True)
def setup_db():
    asyncio.run(_setup())
    # Clear rate limit state between tests so they don't bleed into each other
    import main as main_module
    main_module.failed_logins.clear()
    # Reset slowapi in-memory storage
    try:
        from main import app
        limiter = app.state.limiter
        limiter._storage._storage.clear()
    except Exception:
        pass
    yield
    if os.path.exists("test_aerovhyn.db"):
        try: os.remove("test_aerovhyn.db")
        except: pass

def get_auth_token(client, username="paramedic1", password="rescue123"):
    import main as main_module
    main_module.failed_logins.clear()
    response = client.post("/api/auth/token", json={
        "username": username,
        "password": password
    })
    data = response.json()
    if "access_token" not in data:
        raise RuntimeError(f"Login failed for {username}: {data}")
    return data["access_token"]

def test_route_endpoint_creates_ambulance():
    with TestClient(app) as c:
        token = get_auth_token(c)
        route_payload = {
            "ambulance_lat": 17.4,
            "ambulance_lon": 78.4,
            "vitals": {
                "heart_rate": 150,
                "spo2": 85,
                "systolic_bp": 90,
                "emergency_type": "cardiac",
                "age": 55
            }
        }
        response = c.post(
            "/api/route", 
            json=route_payload,
            headers={"Authorization": f"Bearer {token}"}
        )
        assert response.status_code == 200, response.json()
        data = response.json()
        assert "ambulance_id" in data
        assert "ranked_hospitals" in data
        assert len(data["ranked_hospitals"]) > 0

def test_ambulance_position_update():
    """A paramedic without an ambulance assignment trying to update any ambulance gets 403."""
    with TestClient(app) as c:
        token = get_auth_token(c)
        # Since paramedic1 has no ambulance_id in their JWT (the ambulance lookup
        # fails because no ambulance named AMB-001 exists yet), updating ambulance 1
        # should return 403 ownership error or 404 if the ambulance does not exist.
        update_payload = {"lat": 17.41, "lon": 78.41}
        res = c.put(
            f"/api/ambulances/999/position",
            json=update_payload,
            headers={"Authorization": f"Bearer {token}"}
        )
        assert res.status_code in (403, 404)

def test_unauthorized_position_update():
    """A paramedic without an ambulance assignment gets 403 on any ambulance position update."""
    with TestClient(app) as c:
        # Login as paramedic1 (no ambulance linked to this JWT)
        token = get_auth_token(c)

        # Create an ambulance via route
        route_payload = {
            "ambulance_lat": 17.4, "ambulance_lon": 78.4,
            "vitals": {"heart_rate": 150, "spo2": 85, "systolic_bp": 90, "emergency_type": "cardiac", "age": 55}
        }
        route_res = c.post("/api/route", json=route_payload, headers={"Authorization": f"Bearer {token}"})
        assert route_res.status_code == 200, route_res.json()
        amb_id = route_res.json()["ambulance_id"]

        # Trying to update an ambulance that is NOT in the JWT ambulance_id (which is None)
        # should return 403
        update_payload = {"lat": 17.41, "lon": 78.41}
        res = c.put(
            f"/api/ambulances/{amb_id}/position",
            json=update_payload,
            headers={"Authorization": f"Bearer {token}"}
        )
        assert res.status_code == 403
        assert "ambulance" in res.json()["detail"].lower()
