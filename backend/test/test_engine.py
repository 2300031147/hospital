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
