from sqlalchemy import Column, Integer, String, Float, DateTime, ForeignKey, Text, JSON
from sqlalchemy.orm import declarative_base
from datetime import datetime

Base = declarative_base()

class User(Base):
    __tablename__ = 'users'
    id = Column(Integer, primary_key=True, autoincrement=True)
    username = Column(String, unique=True, nullable=False)
    password_hash = Column(String, nullable=False)
    full_name = Column(String, nullable=False)
    role = Column(String, nullable=False, default='paramedic')
    # Bug #57: Convert ambulance_id to FK integer instead of string collision
    ambulance_id = Column(Integer, ForeignKey('ambulances.id'))
    hospital_id = Column(Integer)
    created_at = Column(DateTime, default=datetime.utcnow)

class Hospital(Base):
    __tablename__ = 'hospitals'
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String, nullable=False)
    lat = Column(Float, nullable=False)
    lon = Column(Float, nullable=False)
    icu_beds = Column(Integer, default=0)
    total_icu_beds = Column(Integer, default=10)
    soft_reserve = Column(Integer, default=0)
    ventilators = Column(Integer, default=0)
    total_ventilators = Column(Integer, default=5)
    specialists = Column(Text, default='[]')
    current_load = Column(Integer, default=0)
    max_capacity = Column(Integer, default=100)
    equipment_score = Column(Float, default=0.8)
    status = Column(String, default='active')
    last_updated = Column(DateTime, default=datetime.utcnow)

class Ambulance(Base):
    __tablename__ = 'ambulances'
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String, default='AMB-001')
    lat = Column(Float, nullable=False)
    lon = Column(Float, nullable=False)
    patient_severity = Column(String, default='unknown')
    destination_hospital_id = Column(Integer, ForeignKey('hospitals.id'))
    status = Column(String, default='idle')
    patient_vitals = Column(Text, default='{}')
    eta_minutes = Column(Float, default=0.0)
    created_at = Column(DateTime, default=datetime.utcnow)

class Log(Base):
    __tablename__ = 'logs'
    id = Column(Integer, primary_key=True, autoincrement=True)
    timestamp = Column(DateTime, default=datetime.utcnow)
    event_type = Column(String, nullable=False)
    ambulance_id = Column(Integer, ForeignKey('ambulances.id'))
    hospital_selected_id = Column(Integer, ForeignKey('hospitals.id'))
    score = Column(Float)
    details = Column(Text, default='')

class Blockchain(Base):
    __tablename__ = 'blockchain'
    idx = Column(Integer, primary_key=True)
    timestamp = Column(String, nullable=False)
    data = Column(Text, nullable=False)
    prev_hash = Column(String, nullable=False)
    hash = Column(String, nullable=False)
    nonce = Column(Integer, default=0)

class HistoricalPattern(Base):
    __tablename__ = 'historical_patterns'
    id = Column(Integer, primary_key=True, autoincrement=True)
    hospital_id = Column(Integer, ForeignKey('hospitals.id'), nullable=False)
    day_of_week = Column(Integer, nullable=False)  # 0=Mon, 6=Sun
    hour_of_day = Column(Integer, nullable=False)  # 0-23
    avg_load = Column(Float, nullable=False)
    avg_turnover_rate = Column(Float, default=0.05)
    
    # Bug #58: Enforce unique constraint so reseeding doesn't duplicate slots
    from sqlalchemy import UniqueConstraint
    __table_args__ = (
        UniqueConstraint('hospital_id', 'day_of_week', 'hour_of_day', name='uq_pattern_slot'),
    )

class SystemSettingsDB(Base):
    __tablename__ = 'settings'
    id = Column(Integer, primary_key=True)
    distance_weight = Column(Float, default=0.2)
    readiness_weight = Column(Float, default=0.5)
    severity_match_weight = Column(Float, default=0.3)
    max_routing_distance_km = Column(Float, default=30.0)
