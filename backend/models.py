"""
AEROVHYN — Pydantic Models
Request/response schemas for the API.
"""

from pydantic import BaseModel, Field, field_validator, model_validator
from typing import Optional, Union
from datetime import datetime
from enum import Enum


# --- Enums ---

class SeverityLevel(str, Enum):
    CRITICAL = "critical"
    MODERATE = "moderate"
    STABLE = "stable"


class EmergencyType(str, Enum):
    CARDIAC = "cardiac"
    TRAUMA = "trauma"
    RESPIRATORY = "respiratory"
    NEUROLOGICAL = "neurological"
    FRACTURE = "fracture"
    BURN = "burn"
    GENERAL = "general"


class AmbulanceStatus(str, Enum):
    IDLE = "idle"
    EN_ROUTE = "en_route"
    ACCEPTED = "accepted"
    AT_SCENE = "at_scene"
    TRANSPORTING = "transporting"
    COMPLETED = "completed"


# --- Request Models ---

class PatientVitals(BaseModel):
    heart_rate: int = Field(..., ge=20, le=250, description="Heart rate in BPM")
    spo2: int = Field(..., ge=0, le=100, description="Blood oxygen saturation %")
    systolic_bp: int = Field(..., ge=40, le=300, description="Systolic blood pressure mmHg")
    emergency_type: EmergencyType = Field(..., description="Type of emergency")
    age: int = Field(..., ge=0, le=120, description="Patient age")


class HospitalCreate(BaseModel):
    name: str
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    icu_beds: int = 0
    total_icu_beds: int = 10
    ventilators: int = 0
    total_ventilators: int = 5
    specialists: list[str] = []
    current_load: int = 0
    max_capacity: int = 100
    # Bug #44: Add range and enum-like validation
    equipment_score: float = Field(0.8, ge=0.0, le=1.0)
    status: str = Field("active", pattern="^(active|inactive|diverted)$")


class SystemSettings(BaseModel):
    distance_weight: float = Field(0.2, ge=0.0, le=1.0)
    readiness_weight: float = Field(0.5, ge=0.0, le=1.0)
    severity_match_weight: float = Field(0.3, ge=0.0, le=1.0)
    max_routing_distance_km: float = Field(30.0, ge=5.0, le=200.0)

    @model_validator(mode='after')
    def weights_must_sum_to_one(self):
        # Bug #43: Ensure engine weights sum to 1.0
        total = self.distance_weight + self.readiness_weight + self.severity_match_weight
        if abs(total - 1.0) > 0.01:
            raise ValueError(f'Weights must sum to 1.0, got {round(total, 3)}')
        return self


class HospitalUpdate(BaseModel):
    icu_beds: Optional[int] = None
    total_icu_beds: Optional[int] = None
    ventilators: Optional[int] = None
    total_ventilators: Optional[int] = None
    specialists: Optional[list[str]] = None
    current_load: Optional[int] = None
    max_capacity: Optional[int] = None
    equipment_score: Optional[float] = None
    status: Optional[str] = None


class AmbulanceCreate(BaseModel):
    name: Optional[str] = "AMB-001"
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    patient_vitals: Optional[PatientVitals] = None


class AmbulancePositionUpdate(BaseModel):
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)


class RouteRequest(BaseModel):
    ambulance_lat: float = Field(..., ge=-90, le=90)
    ambulance_lon: float = Field(..., ge=-180, le=180)
    vitals: PatientVitals


# --- Response Models ---

class SeverityResult(BaseModel):
    level: SeverityLevel
    score: float = Field(..., ge=0, le=1, description="Severity score 0-1")
    reasons: list[str]


class HospitalInfo(BaseModel):
    id: int
    name: str
    lat: float
    lon: float
    icu_beds: int
    total_icu_beds: int
    soft_reserve: int = 0
    ventilators: int
    total_ventilators: int
    specialists: list[str]
    current_load: int
    max_capacity: int
    equipment_score: float
    status: str
    last_updated: Optional[str] = None


class RankedHospital(BaseModel):
    hospital: HospitalInfo
    final_score: float
    readiness_score: float
    distance_score: float
    severity_match_score: float
    distance_km: float
    eta_minutes: float = 0.0


class RouteResponse(BaseModel):
    ambulance_id: int
    severity: SeverityResult
    ranked_hospitals: list[RankedHospital]
    recommended: RankedHospital


class LogEntry(BaseModel):
    id: int
    timestamp: str
    event_type: str
    ambulance_id: Optional[int]
    hospital_selected_id: Optional[int]
    score: Optional[float]
    details: str


class HandoffAlert(BaseModel):
    ambulance_id: int
    hospital_id: int
    hospital_name: str
    severity: SeverityResult
    vitals: PatientVitals
    eta_minutes: float
    prep_instructions: list[str]
    bed_reserved: bool = False


class UserCreate(BaseModel):
    username: str
    password: str
    full_name: str
    role: str = "paramedic"
    ambulance_id: Optional[str] = None
    hospital_id: Optional[int] = None
    
    @field_validator('password')
    @classmethod
    def password_strength(cls, v):
        if len(v) < 8:
            raise ValueError('Password must be at least 8 characters')
        if not any(c.isupper() for c in v):
            raise ValueError('Password must contain an uppercase letter')
        if not any(c.isdigit() for c in v):
            raise ValueError('Password must contain a digit')
        return v

    @field_validator('username')
    @classmethod
    def username_valid(cls, v):
        if len(v) < 3:
            raise ValueError('Username must be at least 3 characters')
        if not v.replace('_', '').replace('-', '').isalnum():
            raise ValueError('Username must be alphanumeric')
        return v


class UserUpdate(BaseModel):
    full_name: Optional[str] = None
    role: Optional[str] = None
    ambulance_id: Optional[str] = None
    hospital_id: Optional[int] = None


class UserResponse(BaseModel):
    id: int
    username: str
    full_name: str
    role: str
    ambulance_id: Optional[str] = None
    hospital_id: Optional[int] = None
    created_at: Union[str, datetime]


class AnalyticsResponse(BaseModel):
    total_dispatches: int
    total_reroutes: int
    severity_distribution: dict
    hospital_utilization: list[dict]
    avg_score: float
    reroute_rate: float
    recent_events: int


class BlockchainBlock(BaseModel):
    index: int
    timestamp: str
    data: dict
    prev_hash: str
    hash: str
