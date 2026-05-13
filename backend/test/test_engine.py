import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from engine import (
    haversine_distance, compute_distance_score, compute_readiness,
    compute_eta, compute_severity_match, rank_hospitals, RankedHospital
)
from models import SeverityLevel, HospitalInfo, EmergencyType


def test_haversine_distance():
    # Known distance test (e.g., two points in a city ~11km apart)
    dist = haversine_distance(17.3850, 78.4867, 17.4401, 78.3489)
    assert 15.0 <= dist <= 17.0

def test_compute_distance_score():
    assert compute_distance_score(0, 30.0) == 1.0
    assert compute_distance_score(15, 30.0) == 0.5
    assert compute_distance_score(30, 30.0) == 0.0
    assert compute_distance_score(40, 30.0) == 0.0

def test_compute_eta():
    assert compute_eta(0) == 0.0
    assert compute_eta(40) > 0.0
    assert compute_eta(40) == 48.0

def test_compute_readiness_no_capacity():
    h = HospitalInfo(id=1, name="H", lat=0, lon=0, max_capacity=100, current_load=100, icu_beds=5, total_icu_beds=5, soft_reserve=0, ventilators=2, total_ventilators=2, specialists=[], equipment_score=1.0, status="active")
    assert compute_readiness(h, SeverityLevel.STABLE, EmergencyType.TRAUMA, 0.5) < 0.6

def test_compute_severity_match_critical():
    h = HospitalInfo(id=1, name="H", lat=0, lon=0, max_capacity=100, current_load=100, icu_beds=5, total_icu_beds=5, soft_reserve=0, ventilators=2, total_ventilators=2, specialists=["cardiology"], equipment_score=0.9, status="active")
    # Match
    match1 = compute_severity_match(h, SeverityLevel.CRITICAL, EmergencyType.CARDIAC)
    assert match1 > 0.8
    # No Match
    match2 = compute_severity_match(h, SeverityLevel.CRITICAL, EmergencyType.TRAUMA)
    assert match2 <= 0.6

import pytest

@pytest.mark.asyncio
async def test_rank_hospitals_empty():
    res = await rank_hospitals([], type("Severity", (), {"level": SeverityLevel.STABLE}), EmergencyType.GENERAL, 0, 0)
    assert res == []

@pytest.mark.asyncio
async def test_rank_hospitals_inactive_filtered():
    h = HospitalInfo(id=1, name="H", lat=0, lon=0, icu_beds=0, total_icu_beds=0, soft_reserve=0, ventilators=0, total_ventilators=0, current_load=0, max_capacity=0, status="inactive", specialists=[], equipment_score=1.0)
    ranked = await rank_hospitals([h], type("Severity", (), {"level": SeverityLevel.STABLE}), EmergencyType.GENERAL, 0, 0)
    assert len(ranked) == 0

@pytest.mark.asyncio
async def test_rank_hospitals_sorting():
    h1 = HospitalInfo(id=1, name="H1", lat=0, lon=0, max_capacity=100, current_load=10, icu_beds=5, total_icu_beds=5, soft_reserve=0, ventilators=2, total_ventilators=2, specialists=[], equipment_score=1.0, status="active")
    h2 = HospitalInfo(id=2, name="H2", lat=1, lon=1, max_capacity=100, current_load=90, icu_beds=1, total_icu_beds=5, soft_reserve=0, ventilators=0, total_ventilators=2, specialists=[], equipment_score=0.5, status="active")
    
    class DummySev:
        level = SeverityLevel.STABLE
        
    ranked = await rank_hospitals([h1, h2], DummySev, EmergencyType.GENERAL, 0, 0)
    assert len(ranked) == 2
    assert ranked[0].hospital.id == 1  # H1 is closer and less loaded
    assert ranked[0].final_score > ranked[1].final_score

def test_compute_readiness_datetime_parsing():
    from datetime import datetime, timezone, timedelta

    now = datetime.utcnow()
    # Mocking different datetime values for Bug #54
    dt_variants = [
        now - timedelta(minutes=40),  # Datetime native
        (now - timedelta(minutes=40)).replace(tzinfo=timezone.utc),  # Datetime aware
        (now - timedelta(minutes=40)).strftime("%Y-%m-%d %H:%M:%S"), # SQLite format string
        (now - timedelta(minutes=40)).strftime("%Y-%m-%d %H:%M:%S.%f"), # SQLite string with micros
        (now - timedelta(minutes=40)).isoformat() + "Z", # Postgres style ISO with Z
    ]

    for dt_val in dt_variants:
        h = HospitalInfo(
            id=1, name="H", lat=0, lon=0, max_capacity=100, current_load=50,
            icu_beds=5, total_icu_beds=10, soft_reserve=0, ventilators=2, total_ventilators=2,
            specialists=["general"], equipment_score=1.0, status="active"
        )
        h.last_updated = dt_val
        # Should apply 20% penalty
        readiness = compute_readiness(h, SeverityLevel.STABLE, EmergencyType.GENERAL, 0.5)

        # Base readiness without penalty approx:
        # icu_score(0.5)*0.3 + specialist(1.0)*0.3 + load(1.0 - 50/100)*0.2 + equip(1.0)*0.2
        # = 0.15 + 0.3 + 0.1 + 0.2 = 0.75
        # Load prediction modifies it slightly, but since last_updated is > 30 mins ago,
        # it should apply the 0.8 penalty.
        # Let's compare with recent last_updated.
        h_recent = h.model_copy(update={"last_updated": datetime.utcnow()}) if hasattr(h, "model_copy") else h.copy(update={"last_updated": datetime.utcnow()})
        readiness_recent = compute_readiness(h_recent, SeverityLevel.STABLE, EmergencyType.GENERAL, 0.5)

        assert readiness < readiness_recent, f"Penalty was not applied for format: {type(dt_val)} {dt_val}"
