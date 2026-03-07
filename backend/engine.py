"""
AEROVHYN — Decision Engine
Core intelligence: severity classification, readiness prediction, ranking, and routing.
"""

import math
import json
from datetime import datetime
from models import (
    PatientVitals,
    SeverityResult,
    SeverityLevel,
    HospitalInfo,
    RankedHospital,
    EmergencyType,
)


# --- Specialty Mapping ---
# Maps emergency types to required hospital specialties
EMERGENCY_SPECIALTY_MAP = {
    EmergencyType.CARDIAC: ["cardiology"],
    EmergencyType.TRAUMA: ["trauma", "orthopedics"],
    EmergencyType.RESPIRATORY: ["pulmonology"],
    EmergencyType.NEUROLOGICAL: ["neurology"],
    EmergencyType.FRACTURE: ["orthopedics", "trauma"],
    EmergencyType.BURN: ["trauma", "general"],
    EmergencyType.GENERAL: ["general"],
}


def classify_severity(vitals: PatientVitals) -> SeverityResult:
    """
    Rule-based severity classification.
    Returns critical / moderate / stable with explainable reasons.

    Scoring:
    - SpO2 < 85 → +0.4 critical
    - Systolic BP < 90 → +0.3 critical
    - Heart rate > 150 or < 40 → +0.2 critical
    - Age > 70 → +0.1 modifier
    - Emergency type adjustments
    """
    score = 0.0
    reasons = []

    # SpO2 check
    if vitals.spo2 < 85:
        score += 0.35
        reasons.append(f"Dangerously low SpO2: {vitals.spo2}%")
    elif vitals.spo2 < 92:
        score += 0.15
        reasons.append(f"Low SpO2: {vitals.spo2}%")

    # Blood pressure check
    if vitals.systolic_bp < 90:
        score += 0.30
        reasons.append(f"Hypotension: BP {vitals.systolic_bp} mmHg")
    elif vitals.systolic_bp > 180:
        score += 0.20
        reasons.append(f"Hypertensive crisis: BP {vitals.systolic_bp} mmHg")

    # Heart rate check
    if vitals.heart_rate > 150:
        score += 0.20
        reasons.append(f"Severe tachycardia: {vitals.heart_rate} BPM")
    elif vitals.heart_rate < 40:
        score += 0.25
        reasons.append(f"Severe bradycardia: {vitals.heart_rate} BPM")
    elif vitals.heart_rate > 120:
        score += 0.10
        reasons.append(f"Tachycardia: {vitals.heart_rate} BPM")

    # Age modifier
    if vitals.age > 70:
        score += 0.08
        reasons.append(f"Elderly patient: age {vitals.age}")
    elif vitals.age < 5:
        score += 0.08
        reasons.append(f"Pediatric patient: age {vitals.age}")

    # Emergency type modifier
    if vitals.emergency_type in (EmergencyType.CARDIAC, EmergencyType.NEUROLOGICAL):
        score += 0.10
        reasons.append(f"High-risk emergency type: {vitals.emergency_type.value}")
    elif vitals.emergency_type in (EmergencyType.TRAUMA, EmergencyType.BURN):
        score += 0.05
        reasons.append(f"Trauma/burn emergency: {vitals.emergency_type.value}")

    # Clamp score
    score = min(score, 1.0)

    # Determine level
    if score >= 0.55:
        level = SeverityLevel.CRITICAL
    elif score >= 0.25:
        level = SeverityLevel.MODERATE
    else:
        level = SeverityLevel.STABLE

    if not reasons:
        reasons.append("All vitals within normal range")

    return SeverityResult(level=level, score=round(score, 3), reasons=reasons)


def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate distance in km between two lat/lon points using Haversine formula."""
    R = 6371  # Earth radius in km
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlon / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c


def compute_readiness(hospital: HospitalInfo, severity_level: SeverityLevel, emergency_type: EmergencyType) -> float:
    """
    Hospital readiness prediction score (0-1).

    Readiness = ICU Availability × 0.3
              + Specialist Match × 0.3
              + Load Prediction  × 0.2
              + Equipment Score  × 0.2
    """
    # ICU availability (0-1): ratio of available ICU beds
    if hospital.total_icu_beds > 0:
        icu_score = hospital.icu_beds / hospital.total_icu_beds
    else:
        icu_score = 0.0

    # For critical patients, ICU matters more
    if severity_level == SeverityLevel.CRITICAL and hospital.icu_beds == 0:
        icu_score = 0.0  # No ICU beds = not ready for critical

    # Specialist match (0-1): do they have the right specialists?
    required_specialists = EMERGENCY_SPECIALTY_MAP.get(emergency_type, ["general"])
    if hospital.specialists:
        matches = sum(1 for s in required_specialists if s in hospital.specialists)
        specialist_score = matches / len(required_specialists) if required_specialists else 0.5
    else:
        specialist_score = 0.2  # Fallback — assume basic capability

    # Load prediction (0-1): Standard live load + predictive forecasting
    # We factor in how long the ambulance takes to arrive. If ETA is > 30 mins, 
    # we predict bed availability based on average turnover rate + baseline load.
    
    # Simulating the Historical fetch based on current ETA and time
    # (In a true prod environment, this could involve an async call before engine ranking. 
    # For Engine performance, we use a predictive weighting heuristic based on time of day).
    from datetime import timedelta
    now_ist = datetime.utcnow() + timedelta(hours=5, minutes=30)
    current_hour = now_ist.hour
    is_weekend = now_ist.weekday() >= 5
    
    predicted_load_ratio = hospital.current_load / hospital.max_capacity if hospital.max_capacity > 0 else 1.0
    
    # Introduce Forecasting if wait > 15m. (e.g. night time + weekend = higher load prediction)
    # Assumes a 5% baseline turnover rate per hour
    eta_hours = 0.5  # Fixed default since we pass ETA around carefully, heuristic for readiness score
    predicted_turnover = 0.05 * eta_hours 
    predicted_load_ratio -= predicted_turnover 

    # Penalty for peak hours
    if 18 <= current_hour <= 23 or is_weekend:
        predicted_load_ratio += 0.1
        
    predicted_load_ratio = max(0, min(predicted_load_ratio, 1.0))

    if hospital.max_capacity > 0:
        load_score = max(0, 1.0 - predicted_load_ratio)
    else:
        load_score = 0.0

    # If hospital is predicted to be nearly full (>90%), heavily penalize
    if predicted_load_ratio > 0.9:
        load_score *= 0.3

    # Equipment score (already 0-1 from database)
    equip_score = hospital.equipment_score

    # Weighted combination
    readiness = (
        icu_score * 0.30
        + specialist_score * 0.30
        + load_score * 0.20
        + equip_score * 0.20
    )

    # Uncertainty Penalty for stale data
    if hospital.last_updated:
        try:
            last_updated_dt = datetime.strptime(hospital.last_updated, "%Y-%m-%d %H:%M:%S")
            now = datetime.utcnow()
            diff_minutes = (now - last_updated_dt).total_seconds() / 60.0
            if diff_minutes > 30:
                readiness *= 0.8  # 20% penalty for stale data
        except Exception:
            pass

    return round(min(max(readiness, 0), 1), 4)


def compute_eta(distance_km: float, speed_kmh: float = 50.0) -> float:
    """
    Estimate time of arrival in minutes.
    Default ambulance speed: 50 km/h (accounting for urban traffic + sirens).
    """
    if distance_km <= 0 or speed_kmh <= 0:
        return 0.0
    return round((distance_km / speed_kmh) * 60, 1)


def compute_distance_score(distance_km: float, max_distance_km: float = 30.0) -> float:
    """
    Convert distance to a 0-1 score where closer = higher score.
    Distances beyond max_distance_km get score 0.
    """
    if distance_km <= 0:
        return 1.0
    if distance_km >= max_distance_km:
        return 0.0
    return round(1.0 - (distance_km / max_distance_km), 4)


def compute_severity_match(hospital: HospitalInfo, severity_level: SeverityLevel, emergency_type: EmergencyType) -> float:
    """
    How well does this hospital match the patient's severity?

    Critical patients need ICU + specialists → penalize hospitals without them.
    Moderate/stable patients are more flexible.
    """
    required_specialists = EMERGENCY_SPECIALTY_MAP.get(emergency_type, ["general"])
    has_specialists = any(s in hospital.specialists for s in required_specialists) if hospital.specialists else False

    if severity_level == SeverityLevel.CRITICAL:
        # Must have ICU and specialists
        if hospital.icu_beds >= 2 and has_specialists:
            return 1.0
        elif hospital.icu_beds >= 1:
            return 0.6
        elif has_specialists:
            return 0.4
        else:
            return 0.1

    elif severity_level == SeverityLevel.MODERATE:
        if has_specialists:
            return 0.9
        elif hospital.icu_beds >= 1:
            return 0.7
        else:
            return 0.5

    else:  # STABLE
        return 0.8 if has_specialists else 0.6


def get_prep_instructions(severity_level: SeverityLevel, emergency_type: EmergencyType) -> list[str]:
    """
    Generate preparation instructions for hospital handoff based on severity and emergency type.
    """
    instructions = []

    if severity_level == SeverityLevel.CRITICAL:
        instructions.append("⚠️ CRITICAL PATIENT — Prepare crash cart")
        instructions.append("Alert senior attending physician")

    if emergency_type == EmergencyType.CARDIAC:
        instructions.extend([
            "Prepare ECG/defibrillator",
            "Ready catheterization lab if available",
            "Prepare IV nitroglycerin and aspirin",
        ])
    elif emergency_type == EmergencyType.TRAUMA:
        instructions.extend([
            "Prepare trauma bay",
            "Ready blood bank (O-negative on standby)",
            "Alert surgical team",
        ])
    elif emergency_type == EmergencyType.RESPIRATORY:
        instructions.extend([
            "Prepare ventilator",
            "Ready intubation kit",
            "Prepare bronchodilator nebulization",
        ])
    elif emergency_type == EmergencyType.NEUROLOGICAL:
        instructions.extend([
            "Prepare CT scanner",
            "Ready tPA for stroke if applicable",
            "Alert neurology team",
        ])
    elif emergency_type == EmergencyType.FRACTURE:
        instructions.extend([
            "Prepare X-ray / imaging",
            "Ready splinting materials",
            "Alert orthopedics team",
        ])
    elif emergency_type == EmergencyType.BURN:
        instructions.extend([
            "Prepare burn care supplies",
            "Ready IV fluids (Ringer's lactate)",
            "Alert burn unit if available",
        ])
    else:
        instructions.append("Prepare standard triage assessment")

    if severity_level == SeverityLevel.CRITICAL:
        instructions.append("System auto-locked 1 ICU bed for this arrival.")

    return instructions


def rank_hospitals(
    hospitals: list[HospitalInfo],
    severity: SeverityResult,
    emergency_type: EmergencyType,
    amb_lat: float,
    amb_lon: float,
    weights: dict = None,
) -> list[RankedHospital]:
    """
    Rank all active hospitals by final score.

    Final Score = Readiness × W_R + Distance Score × W_D + Severity Match × W_S

    Returns sorted list (highest score first).
    """
    if weights is None:
        weights = {
            "distance_weight": 0.2,
            "readiness_weight": 0.5,
            "severity_match_weight": 0.3,
            "max_routing_distance_km": 30.0
        }

    ranked = []

    for hospital in hospitals:
        if hospital.status != "active":
            continue

        distance_km = haversine_distance(amb_lat, amb_lon, hospital.lat, hospital.lon)
        readiness = compute_readiness(hospital, severity.level, emergency_type)
        dist_score = compute_distance_score(distance_km, weights["max_routing_distance_km"])
        sev_match = compute_severity_match(hospital, severity.level, emergency_type)
        eta = compute_eta(distance_km)

        if severity.level == SeverityLevel.CRITICAL:
            # Priority to Specialty Match over Distance for Critical
            s_weight = max(weights["severity_match_weight"], 0.4)
            r_weight = max(weights["readiness_weight"] - 0.1, 0.2)
            d_weight = weights["distance_weight"]
            
            # Normalize to 1.0
            total = s_weight + r_weight + d_weight
            s_weight, r_weight, d_weight = s_weight/total, r_weight/total, d_weight/total
            
            final_score = readiness * r_weight + dist_score * d_weight + sev_match * s_weight
        else:
            final_score = readiness * weights["readiness_weight"] + dist_score * weights["distance_weight"] + sev_match * weights["severity_match_weight"]

        ranked.append(
            RankedHospital(
                hospital=hospital,
                final_score=round(final_score, 4),
                readiness_score=readiness,
                distance_score=dist_score,
                severity_match_score=sev_match,
                distance_km=round(distance_km, 2),
                eta_minutes=eta,
            )
        )

    ranked.sort(key=lambda x: x.final_score, reverse=True)
    return ranked

