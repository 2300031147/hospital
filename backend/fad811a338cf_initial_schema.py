"""Initial schema — full CREATE TABLE

Revision ID: fad811a338cf
Revises: 
Create Date: 2026-03-07 17:44:16.428888

BUG-7 FIX: Previous migration only had ALTER TABLE statements which require
tables to already exist. Replaced with full CREATE TABLE IF NOT EXISTS so
this works on a fresh PostgreSQL instance.

Also includes ambulance_id as Integer FK (not String) per Bug #57 fix.
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa


revision: str = 'fad811a338cf'
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create all tables from scratch on a fresh database."""

    op.create_table(
        'hospitals',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('name', sa.String(), nullable=False),
        sa.Column('lat', sa.Float(), nullable=False),
        sa.Column('lon', sa.Float(), nullable=False),
        sa.Column('icu_beds', sa.Integer(), server_default='0'),
        sa.Column('total_icu_beds', sa.Integer(), server_default='10'),
        sa.Column('soft_reserve', sa.Integer(), server_default='0'),
        sa.Column('ventilators', sa.Integer(), server_default='0'),
        sa.Column('total_ventilators', sa.Integer(), server_default='5'),
        sa.Column('specialists', sa.Text(), server_default="'[]'"),
        sa.Column('current_load', sa.Integer(), server_default='0'),
        sa.Column('max_capacity', sa.Integer(), server_default='100'),
        sa.Column('equipment_score', sa.Float(), server_default='0.8'),
        sa.Column('status', sa.String(), server_default="'active'"),
        sa.Column('last_updated', sa.DateTime(), server_default=sa.text('NOW()')),
    )

    op.create_table(
        'ambulances',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('name', sa.String(), server_default="'AMB-001'"),
        sa.Column('lat', sa.Float(), nullable=False),
        sa.Column('lon', sa.Float(), nullable=False),
        sa.Column('patient_severity', sa.String(), server_default="'unknown'"),
        sa.Column('destination_hospital_id', sa.Integer(), sa.ForeignKey('hospitals.id'), nullable=True),
        sa.Column('emergency_type', sa.String(), nullable=True),
        sa.Column('status', sa.String(), server_default="'idle'"),
        sa.Column('patient_vitals', sa.Text(), server_default="'{}'"),
        sa.Column('eta_minutes', sa.Float(), server_default='0'),
        sa.Column('created_at', sa.DateTime(), server_default=sa.text('NOW()')),
    )

    op.create_table(
        'users',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('username', sa.String(), unique=True, nullable=False),
        sa.Column('password_hash', sa.String(), nullable=False),
        sa.Column('full_name', sa.String(), nullable=False),
        sa.Column('role', sa.String(), nullable=False, server_default="'paramedic'"),
        # BUG-5/57 FIX: Integer FK instead of String/Text
        sa.Column('ambulance_id', sa.Integer(), sa.ForeignKey('ambulances.id'), nullable=True),
        sa.Column('hospital_id', sa.Integer(), nullable=True),
        sa.Column('created_at', sa.DateTime(), server_default=sa.text('NOW()')),
    )

    op.create_table(
        'logs',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('timestamp', sa.DateTime(), server_default=sa.text('NOW()')),
        sa.Column('event_type', sa.String(), nullable=False),
        sa.Column('ambulance_id', sa.Integer(), sa.ForeignKey('ambulances.id'), nullable=True),
        sa.Column('hospital_selected_id', sa.Integer(), sa.ForeignKey('hospitals.id'), nullable=True),
        sa.Column('score', sa.Float(), nullable=True),
        sa.Column('details', sa.Text(), server_default="''"),
    )

    op.create_table(
        'blockchain',
        sa.Column('idx', sa.Integer(), primary_key=True),
        sa.Column('timestamp', sa.String(), nullable=False),
        sa.Column('data', sa.Text(), nullable=False),
        sa.Column('prev_hash', sa.String(), nullable=False),
        sa.Column('hash', sa.String(), nullable=False),
        sa.Column('nonce', sa.Integer(), server_default='0'),
    )

    op.create_table(
        'historical_patterns',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('hospital_id', sa.Integer(), sa.ForeignKey('hospitals.id'), nullable=False),
        sa.Column('day_of_week', sa.Integer(), nullable=False),
        sa.Column('hour_of_day', sa.Integer(), nullable=False),
        sa.Column('avg_load', sa.Float(), nullable=False),
        sa.Column('avg_turnover_rate', sa.Float(), server_default='0.05'),
        sa.UniqueConstraint('hospital_id', 'day_of_week', 'hour_of_day', name='uq_pattern_slot'),
    )

    op.create_table(
        'settings',
        sa.Column('id', sa.Integer(), primary_key=True),
        sa.Column('distance_weight', sa.Float(), server_default='0.2'),
        sa.Column('readiness_weight', sa.Float(), server_default='0.5'),
        sa.Column('severity_match_weight', sa.Float(), server_default='0.3'),
        sa.Column('max_routing_distance_km', sa.Float(), server_default='30.0'),
    )

    # Seed default settings row
    op.execute("INSERT INTO settings (id, distance_weight, readiness_weight, severity_match_weight, max_routing_distance_km) VALUES (1, 0.2, 0.5, 0.3, 30.0) ON CONFLICT(id) DO NOTHING")


def downgrade() -> None:
    """Drop all tables in reverse FK dependency order."""
    op.drop_table('historical_patterns')
    op.drop_table('blockchain')
    op.drop_table('logs')
    op.drop_table('settings')
    op.drop_table('users')
    op.drop_table('ambulances')
    op.drop_table('hospitals')
