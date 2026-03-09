"""
AEROVHYN v2 — Main FastAPI Application
REST API + WebSocket + Handoff + Bed Reservation + Conflict Resolution + Analytics + Blockchain
"""

# v2.1 — Added middleware, caching, bug fixes

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect, BackgroundTasks, Depends, status, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import json
import asyncio
import os
import time
from datetime import datetime, timedelta
from dotenv import load_dotenv

load_dotenv()

from database import init_db, seed_data, get_db, verify_password, hash_password
from models import (
    PatientVitals,
    SeverityResult,
    HospitalInfo,
    HospitalCreate,
    HospitalUpdate,
    RankedHospital,
    RouteRequest,
    RouteResponse,
    AmbulanceCreate,
    AmbulancePositionUpdate,
    LogEntry,
    HandoffAlert,
    AnalyticsResponse,
    EmergencyType,
    SeverityLevel,
    UserCreate,
    UserUpdate,
    UserResponse,
    SystemSettings,
)
from engine import classify_severity, rank_hospitals, haversine_distance, compute_eta, get_prep_instructions
from websocket_manager import manager
from audit_log import init_blockchain_table, add_block, get_chain, verify_chain
from notification_service import dispatch_critical_alerts
from auth import require_hospital_admin, require_paramedic, require_command_center, create_access_token, ACCESS_TOKEN_EXPIRE_MINUTES, TokenData
from middleware import RequestIDMiddleware, RequestTimingMiddleware, RequestLoggingMiddleware, GlobalExceptionMiddleware, SecurityHeadersMiddleware
from cache import cache
from logger import get_logger

log = get_logger("aerovhyn.api")

from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    await seed_data()
    await init_blockchain_table()
    cleanup_task = asyncio.create_task(cleanup_stale_reservations_loop())
    cache.start_cleanup()
    log.info("AEROVHYN v2.1 started", extra={"middleware": "active", "cache": "active", "lifecycle": "lifespan"})
    yield
    cleanup_task.cancel()

app = FastAPI(
    title="AEROVHYN",
    description="Real-Time Hospital Readiness & Ambulance Routing System v2",
    version="2.1.0",
    lifespan=lifespan,
)

limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# Use environment variables for allowed origins, defaulting to standard dev ports
ALLOWED_ORIGINS = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:5174").split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE"],
    allow_headers=["Authorization", "Content-Type"],
)

# Request middleware stack (order matters — innermost last in add_middleware LIFO)
app.add_middleware(GlobalExceptionMiddleware)
app.add_middleware(SecurityHeadersMiddleware)
app.add_middleware(RequestLoggingMiddleware)
app.add_middleware(RequestTimingMiddleware)
app.add_middleware(RequestIDMiddleware)  # Runs first (outermost) to set request_id

# Simple in-memory rate limit store for IP locking


# --- Startup via Lifespan ---


# --- Authentication ---

class LoginRequest(BaseModel):
    username: str
    password: str

@app.post("/api/auth/token")
@limiter.limit("5/minute")
async def login_for_access_token(req: LoginRequest, request: Request, response: Response):
    """
    Authenticate an ambulance crew member (paramedic/driver).
    Returns a JWT access token on success.
    """
    client_ip = request.client.host if request.client else "127.0.0.1"
    
    # Rate Limiting: max 5 failed attempts per 5 minutes using Redis via cache abstraction
    rate_limit_key = f"failed_logins:{client_ip}"
    failed_count = await cache.get(rate_limit_key) or 0
    
    if failed_count >= 5:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Too many failed login attempts. Please try again later."
        )

    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM users WHERE username = ?", (req.username,))
        user = await cursor.fetchone()
    finally:
        await db.close()

    if not user or not verify_password(req.password, user["password_hash"]):
        # Increment failed count with a rolling 5-minute TTL
        await cache.set(rate_limit_key, failed_count + 1, ttl=300)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Success: clear failed attempts footprint
    await cache.delete(rate_limit_key)

    # Bug #57 Side Effect: user["ambulance_id"] is already an integer PK now
    ambulance_db_id = user["ambulance_id"] if user["role"] == "paramedic" else None

    access_token = create_access_token(
        data={
            "sub": user["username"],
            "role": user["role"],
            "hospital_id": user["hospital_id"],
            "user_id": user["id"],
            "ambulance_id": ambulance_db_id,
        },
        expires_delta=timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES),
    )

    # Set HttpOnly, SameSite=Lax cookie to protect token against XSS.
    # We still return access_token in JSON for legacy/compatibility.
    response.set_cookie(
        key="access_token",
        value=f"Bearer {access_token}",
        httponly=True,
        samesite="strict",
        secure=os.getenv("ENV", "development") == "production",
        max_age=ACCESS_TOKEN_EXPIRE_MINUTES * 60,
    )

    # Bug #48: Standardize ambulance_id as integer in login response to match JWT
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "role": user["role"],
        "full_name": user["full_name"],
        "user_id": user["id"],
        "ambulance_id": ambulance_db_id,
        "hospital_id": user["hospital_id"],
    }


# --- User Management ---

@app.get("/api/users", response_model=list[UserResponse])
async def get_users(token=Depends(require_command_center)):
    db = await get_db()
    try:
        cursor = await db.execute("SELECT id, username, full_name, role, ambulance_id, hospital_id, created_at FROM users ORDER BY id DESC")
        rows = await cursor.fetchall()
        return [dict(r) for r in rows]
    finally:
        await db.close()

@app.post("/api/users", response_model=UserResponse)
async def create_user(user: UserCreate, token=Depends(require_command_center)):
    db = await get_db()
    try:
        # Check if exists
        cursor = await db.execute("SELECT id FROM users WHERE username = ?", (user.username,))
        if await cursor.fetchone():
            raise HTTPException(status_code=400, detail="Username already exists")
            
        hashed_pw = hash_password(user.password)
        cursor = await db.execute(
            """INSERT INTO users (username, password_hash, full_name, role, ambulance_id, hospital_id) 
               VALUES (?, ?, ?, ?, ?, ?)""",
            (user.username, hashed_pw, user.full_name, user.role, user.ambulance_id, user.hospital_id)
        )
        await db.commit()
        
        # Fetch created user
        c2 = await db.execute("SELECT id, username, full_name, role, ambulance_id, hospital_id, created_at FROM users WHERE id = ?", (cursor.lastrowid,))
        new_user = await c2.fetchone()
        return dict(new_user)
    finally:
        await db.close()

ALLOWED_USER_FIELDS = {'full_name', 'role', 'ambulance_id', 'hospital_id'}

@app.put("/api/users/{user_id}", response_model=UserResponse)
async def update_user(user_id: int, update: UserUpdate, token=Depends(require_command_center)):
    db = await get_db()
    try:
        fields = []
        values = []
        for key, val in update.model_dump(exclude_unset=True).items():
            if key not in ALLOWED_USER_FIELDS:
                raise HTTPException(status_code=400, detail=f"Invalid field: {key}")
            fields.append(f"{key} = ?")
            values.append(val)
            
        if not fields:
            raise HTTPException(status_code=400, detail="No fields to update")
            
        values.append(user_id)
        await db.execute(f"UPDATE users SET {', '.join(fields)} WHERE id = ?", values)
        await db.commit()
        
        cursor = await db.execute("SELECT id, username, full_name, role, ambulance_id, hospital_id, created_at FROM users WHERE id = ?", (user_id,))
        updated = await cursor.fetchone()
        if not updated:
            raise HTTPException(status_code=404, detail="User not found")
        return dict(updated)
    finally:
        await db.close()

@app.delete("/api/users/{user_id}")
async def delete_user(user_id: int, token=Depends(require_command_center)):
    # Prevent deleting yourself
    if token.user_id == user_id:
        raise HTTPException(status_code=400, detail="Cannot delete your own account")
        
    db = await get_db()
    try:
        cursor = await db.execute("DELETE FROM users WHERE id = ?", (user_id,))
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="User not found")
        await db.commit()
        return {"status": "deleted", "id": user_id}
    finally:
        await db.close()

class PasswordReset(BaseModel):
    new_password: str

@app.put("/api/users/{user_id}/password")
async def reset_password(user_id: int, body: PasswordReset, token=Depends(require_command_center)):
    db = await get_db()
    try:
        hashed_pw = hash_password(body.new_password)
        cursor = await db.execute("UPDATE users SET password_hash = ? WHERE id = ?", (hashed_pw, user_id))
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="User not found")
        await db.commit()
        return {"status": "success", "message": "Password updated"}
    finally:
        await db.close()


# --- Helpers ---

def row_to_hospital(row) -> HospitalInfo:
    specialists = row["specialists"]
    if isinstance(specialists, str):
        try:
            specialists = json.loads(specialists)
        except json.JSONDecodeError:
            specialists = []
    return HospitalInfo(
        id=row["id"],
        name=row["name"],
        lat=row["lat"],
        lon=row["lon"],
        icu_beds=row["icu_beds"],
        total_icu_beds=row["total_icu_beds"],
        soft_reserve=row["soft_reserve"] if "soft_reserve" in row.keys() else 0,
        ventilators=row["ventilators"],
        total_ventilators=row["total_ventilators"],
        specialists=specialists,
        current_load=row["current_load"],
        max_capacity=row["max_capacity"],
        equipment_score=row["equipment_score"],
        status=row["status"],
        last_updated=row["last_updated"] if "last_updated" in row.keys() else None,
    )


async def log_event(event_type: str, ambulance_id=None, hospital_id=None, score=None, details="", db=None):
    close_db = False
    if db is None:
        db = await get_db()
        close_db = True
    try:
        await db.execute(
            "INSERT INTO logs (event_type, ambulance_id, hospital_selected_id, score, details) VALUES (?,?,?,?,?)",
            (event_type, ambulance_id, hospital_id, score, details),
        )
        if close_db:
            await db.commit()
    finally:
        if close_db:
            await db.close()


async def reserve_bed(hospital_id: int, db=None):
    """Reserve an ICU bed at the hospital. Decrements icu_beds, increments soft_reserve."""
    close_db = False
    if db is None:
        db = await get_db()
        close_db = True
    try:
        # Atomic update: only updates if icu_beds > 0
        cursor = await db.execute(
            "UPDATE hospitals SET icu_beds = icu_beds - 1, soft_reserve = soft_reserve + 1 WHERE id = ? AND icu_beds > 0",
            (hospital_id,),
        )
        if close_db:
            await db.commit()
        # Bug 50: Invalidate cache after bed reservation
        if cursor.rowcount > 0:
            await cache.invalidate_prefix("hospitals:")
        # If rowcount > 0, the bed was successfully reserved
        return cursor.rowcount > 0
    finally:
        if close_db:
            await db.close()


async def release_bed(hospital_id: int, db=None):
    """Release a reserved bed back to available."""
    close_db = False
    if db is None:
        db = await get_db()
        close_db = True
    try:
        cursor = await db.execute(
            "UPDATE hospitals SET icu_beds = icu_beds + 1, soft_reserve = soft_reserve - 1 WHERE id = ? AND soft_reserve > 0",
            (hospital_id,),
        )
        if close_db:
            await db.commit()
        
        # Bug 50: Invalidate cache after bed release
        if cursor.rowcount > 0:
            await cache.invalidate_prefix("hospitals:")
            
        return cursor.rowcount > 0
    finally:
        if close_db:
            await db.close()


async def cleanup_stale_reservations_loop():
    """Background task to release beds if an ambulance doesn't arrive within ETA + 10 mins."""
    while True:
        await asyncio.sleep(60)  # Check every 60 seconds
        try:
            db = await get_db()
            try:
                # Fetch en_route ambulances (ignoring accepted patients)
                cursor = await db.execute("SELECT id, destination_hospital_id, created_at, eta_minutes, patient_severity FROM ambulances WHERE status = 'en_route'")
                rows = await cursor.fetchall()
                
                now = datetime.utcnow()
                for row in rows:
                    if not row["created_at"]:
                        continue
                    try:
                        # created_at is default CURRENT_TIMESTAMP which is UTC in SQLite
                        created_dt = datetime.strptime(row["created_at"], "%Y-%m-%d %H:%M:%S")
                        elapsed_mins = (now - created_dt).total_seconds() / 60.0
                        
                        if elapsed_mins > (row["eta_minutes"] + 10):
                            # Timeout exceeded! Release the bed.
                            amb_id = row["id"]
                            hosp_id = row["destination_hospital_id"]
                            
                            async with db.conn.transaction():
                                # Update ambulance status
                                await db.execute("UPDATE ambulances SET status = 'timeout' WHERE id = ?", (amb_id,))
                                
                                # Release the bed directly using release_bed with shared db
                                if row["patient_severity"] == SeverityLevel.CRITICAL.value:
                                    await release_bed(hosp_id, db=db)
                                
                                # Log the event
                                await log_event(
                                    "reservation_timeout", 
                                    ambulance_id=amb_id, 
                                    hospital_id=hosp_id, 
                                    details=f"Reservation timed out after {round(elapsed_mins, 1)} min without arrival",
                                    db=db
                                )
                            
                            # Fetch hospital for broadcast
                            h_cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hosp_id,))
                            h_row = await h_cursor.fetchone()
                            if h_row:
                                hospital = row_to_hospital(h_row)
                                await manager.broadcast({
                                    "type": "alert",
                                    "message": f"Timeout: Ambulance {amb_id} failed to arrive. Bed released at {hospital.name}."
                                })
                                await manager.broadcast({
                                    "type": "bed_released",
                                    "hospital_id": hosp_id,
                                    "hospital_name": hospital.name,
                                    "icu_beds": hospital.icu_beds,
                                    "soft_reserve": hospital.soft_reserve,
                                })
                    except Exception as e:
                        log.error("Cleanup parse error", extra={"error": str(e), "ambulance_id": row.get("id")})
                        
                await db.commit()
            finally:
                await db.close()
        except Exception as e:
            log.error("Cleanup loop error", extra={"error": str(e)})


async def check_and_resolve_conflicts(new_ambulance_id: int, target_hospital_id: int, new_severity: SeverityResult, new_distance_km: float, db=None):
    """
    Multi-ambulance conflict resolution.
    If another ambulance is en-route to the same hospital, compare and possibly reroute the lower-priority one.
    """
    close_db = False
    if db is None:
        db = await get_db()
        close_db = True
    try:
        cursor = await db.execute(
            "SELECT id, lat, lon, patient_severity, patient_vitals FROM ambulances WHERE destination_hospital_id = ? AND status = 'en_route' AND id != ?",
            (target_hospital_id, new_ambulance_id),
        )
        conflicts = await cursor.fetchall()

        if not conflicts:
            return None

        # Get all hospitals for re-ranking
        h_cursor = await db.execute("SELECT * FROM hospitals WHERE status = 'active'")
        h_rows = await h_cursor.fetchall()
        all_hospitals = [row_to_hospital(r) for r in h_rows]
        
        # Get settings weights
        s_cursor = await db.execute("SELECT * FROM settings WHERE id = 1")
        s_row = await s_cursor.fetchone()
        settings = dict(s_row) if s_row else None
        max_routing_distance_km = settings.get("max_routing_distance_km", 30.0) if settings else 30.0

        rerouted = []
        for conflict in conflicts:
            conflict_id = conflict["id"]
            conflict_severity_str = conflict["patient_severity"]
            conflict_lat = conflict["lat"]
            conflict_lon = conflict["lon"]

            # Parse conflict vitals
            try:
                conflict_vitals_data = json.loads(conflict["patient_vitals"])
                conflict_vitals = PatientVitals(**conflict_vitals_data)
            except Exception:
                continue

            conflict_severity = classify_severity(conflict_vitals)

            # Priority comparison: higher severity score wins; closer ambulance is tiebreaker
            new_priority = new_severity.score + (1.0 - min(new_distance_km / max_routing_distance_km, 1.0)) * 0.1

            # Get actual distance for conflict ambulance to target hospital
            h_target = next((h for h in all_hospitals if h.id == target_hospital_id), None)
            if h_target:
                conflict_distance = haversine_distance(conflict_lat, conflict_lon, h_target.lat, h_target.lon)
                conflict_priority = conflict_severity.score + (1.0 - min(conflict_distance / max_routing_distance_km, 1.0)) * 0.1
            else:
                # Fallback if hospital not found — should not happen in normal operation
                conflict_priority = conflict_severity.score

            # The lower priority ambulance gets rerouted
            if new_priority >= conflict_priority:
                # Reroute the existing ambulance
                amb_to_reroute = conflict_id
                amb_lat, amb_lon = conflict_lat, conflict_lon
                reroute_vitals = conflict_vitals
                reroute_severity = conflict_severity
            else:
                # Reroute the new ambulance — caller handles this
                return {"reroute_new": True, "reason": f"Conflict with higher-priority ambulance #{conflict_id}"}
            
            # Re-rank for the rerouted ambulance
            alt_ranked = await rank_hospitals(
                all_hospitals, reroute_severity, reroute_vitals.emergency_type, amb_lat, amb_lon, weights=settings
            )
            
            alt = None
            for potential_alt in alt_ranked:
                if potential_alt.hospital.id != target_hospital_id:
                    if reroute_severity.level == SeverityLevel.CRITICAL:
                        # TRY to reserve the bed
                        success = await reserve_bed(potential_alt.hospital.id, db=db)
                        if success:
                            await release_bed(target_hospital_id, db=db)
                            alt = potential_alt
                            break # Successfully locked a bed, stop searching
                        else:
                            continue # Bed was full, try the next hospital
                    else:
                        alt = potential_alt
                        break

            if alt:
                await db.execute(
                    "UPDATE ambulances SET destination_hospital_id = ?, eta_minutes = ? WHERE id = ?",
                    (alt.hospital.id, alt.eta_minutes, amb_to_reroute),
                )
                if close_db:
                    await db.commit()

                rerouted.append({
                    "ambulance_id": amb_to_reroute,
                    "from_hospital": target_hospital_id,
                    "to_hospital": alt.hospital.id,
                    "to_hospital_name": alt.hospital.name,
                    "reason": "conflict_resolution",
                })

                await log_event(
                    "conflict_resolved",
                    ambulance_id=amb_to_reroute,
                    hospital_id=alt.hospital.id,
                    score=alt.final_score,
                    details=f"Conflict at hospital {target_hospital_id}, rerouted to {alt.hospital.name}",
                    db=db
                )

                await manager.broadcast_reroute(
                    amb_to_reroute, 
                    target_hospital_id, 
                    {"id": alt.hospital.id, "name": alt.hospital.name, "lat": alt.hospital.lat, "lon": alt.hospital.lon}, 
                    "Higher priority patient incoming"
                )

        return rerouted if rerouted else None
    finally:
        if close_db:
            await db.close()


# --- Health ---

@app.get("/api/health")
async def health():
    return {
        "status": "ok",
        "system": "AEROVHYN",
        "version": "2.1.0",
        "ws_stats": manager.get_stats(),
        "cache_stats": await cache.stats(),
    }


# --- Severity Classification ---

@app.post("/api/classify", response_model=SeverityResult)
async def classify(vitals: PatientVitals):
    return classify_severity(vitals)


# --- Hospitals ---

@app.get("/api/hospitals")
async def get_hospitals():
    # Check cache first (10s TTL - hospitals don't change that fast)
    cached_result = await cache.get("hospitals:all")
    if cached_result is not None:
        return cached_result

    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM hospitals ORDER BY name")
        rows = await cursor.fetchall()
        result = [row_to_hospital(r) for r in rows]
        # Pydantic objects are not JSON serializable for Redis mode
        result_dicts = [h.model_dump() for h in result]
        await cache.set("hospitals:all", result_dicts, ttl=10)
        return result
    finally:
        await db.close()


@app.get("/api/hospitals/{hospital_id}")
async def get_hospital(hospital_id: int):
    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hospital_id,))
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Hospital not found")
        return row_to_hospital(row)
    finally:
        await db.close()

@app.post("/api/hospitals")
async def create_hospital(hospital: HospitalCreate, token=Depends(require_command_center)):
    db = await get_db()
    try:
        cursor = await db.execute(
            """INSERT INTO hospitals (name, lat, lon, icu_beds, total_icu_beds, 
               ventilators, total_ventilators, specialists, current_load, 
               max_capacity, equipment_score, status)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (hospital.name, hospital.lat, hospital.lon, hospital.icu_beds, hospital.total_icu_beds,
             hospital.ventilators, hospital.total_ventilators, json.dumps(hospital.specialists),
             hospital.current_load, hospital.max_capacity, hospital.equipment_score, hospital.status)
        )
        await db.commit()
        
        # Initial historical seeding
        h_id = cursor.lastrowid
        for day in range(7):
            for hour in range(24):
                base_load = 0.6
                if 18 <= hour <= 23: base_load += 0.2
                if day >= 5: base_load += 0.1
                base_turnover = 0.05
                await db.execute(
                    "INSERT INTO historical_patterns (hospital_id, day_of_week, hour_of_day, avg_load, avg_turnover_rate) VALUES (?, ?, ?, ?, ?)",
                    (h_id, day, hour, min(base_load, 1.0), base_turnover)
                )
        await db.commit()
        
        c2 = await db.execute("SELECT * FROM hospitals WHERE id = ?", (h_id,))
        new_row = await c2.fetchone()
        await db.commit()
        await cache.invalidate_prefix("hospitals:")
        return row_to_hospital(new_row)
    finally:
        await db.close()


ALLOWED_HOSPITAL_FIELDS = {
    'icu_beds', 'total_icu_beds', 'ventilators', 
    'total_ventilators', 'specialists', 'current_load',
    'max_capacity', 'equipment_score', 'status'
}

@app.put("/api/hospitals/{hospital_id}")
async def update_hospital(hospital_id: int, update: HospitalUpdate, token=Depends(require_hospital_admin)):
    # Verify the logged in user is actually allowed to update THIS hospital
    if token.role == "hospital_admin" and token.hospital_id != hospital_id:
        raise HTTPException(status_code=403, detail="Not authorized to edit this specific hospital")

    db = await get_db()
    try:
        fields = []
        values = []
        for key, val in update.model_dump(exclude_unset=True).items():
            if key not in ALLOWED_HOSPITAL_FIELDS:
                raise HTTPException(status_code=400, detail=f"Invalid field: {key}")
            if key == "specialists":
                fields.append("specialists = ?")
                values.append(json.dumps(val))
            else:
                fields.append(f"{key} = ?")
                values.append(val)

        if not fields:
            raise HTTPException(status_code=400, detail="No fields to update")

        values.append(hospital_id)
        await db.execute(f"UPDATE hospitals SET {', '.join(fields)} WHERE id = ?", values)
        await db.commit()

        cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hospital_id,))
        row = await cursor.fetchone()
        hospital = row_to_hospital(row)
        await cache.invalidate_prefix("hospitals:")

        await manager.broadcast({
            "type": "hospital_update",
            "hospital": hospital.model_dump(),
        })

        return hospital
    finally:
        await db.close()

@app.delete("/api/hospitals/{hospital_id}")
async def delete_hospital(hospital_id: int, token=Depends(require_command_center)):
    db = await get_db()
    try:
        # Check active ambulances
        cursor = await db.execute("SELECT COUNT(*) as c FROM ambulances WHERE destination_hospital_id = ? AND status != 'completed'", (hospital_id,))
        active_count = (await cursor.fetchone())["c"]
        if active_count > 0:
            raise HTTPException(status_code=400, detail="Cannot delete hospital with active incoming ambulances")
            
        cursor = await db.execute("DELETE FROM hospitals WHERE id = ?", (hospital_id,))
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="Hospital not found")
        
        await db.execute("DELETE FROM historical_patterns WHERE hospital_id = ?", (hospital_id,))
        
        # We also need to nullify the user attachments
        await db.execute("UPDATE users SET hospital_id = NULL WHERE hospital_id = ?", (hospital_id,))
        
        await db.commit()
        
        await cache.invalidate_prefix("hospitals:")

        await manager.broadcast({
            "type": "alert",
            "message": f"Hospital #{hospital_id} removed from system."
        })
        
        return {"status": "deleted", "id": hospital_id}
    finally:
        await db.close()


# --- Routing (Core Pipeline) ---

@app.post("/api/route", response_model=RouteResponse)
@limiter.limit("30/minute")
async def route_ambulance(req: RouteRequest, request: Request, background_tasks: BackgroundTasks, token: TokenData = Depends(require_paramedic)):
    severity = classify_severity(req.vitals)

    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM hospitals WHERE status = 'active'")
        rows = await cursor.fetchall()
        hospitals = [row_to_hospital(r) for r in rows]
        
        # Pull weights
        s_cursor = await db.execute("SELECT * FROM settings WHERE id = 1")
        s_row = await s_cursor.fetchone()
        settings = dict(s_row) if s_row else None
    finally:
        await db.close()

    ranked = await rank_hospitals(hospitals, severity, req.vitals.emergency_type, req.ambulance_lat, req.ambulance_lon, weights=settings)

    if not ranked:
        raise HTTPException(status_code=404, detail="No available hospitals")

    best = ranked[0]

    # Check for multi-ambulance conflicts BEFORE reserving the bed
    # We pass 0 as placeholder because we don't have the DB ID yet (INSERT happens below)
    # But it's better to INSERT first or handle the conflict logic correctly.
    # We will insert first to get the ID, then resolve conflicts.
    
    # Create ambulance record and resolve conflicts atomically
    current_ambulance_id = token.ambulance_id
    if not current_ambulance_id:
        raise HTTPException(status_code=400, detail="Paramedic token missing ambulance_id")

    db = await get_db()
    try:
        async with db.conn.transaction():
            # Bug #32: Update existing instead of INSERT for paramedics
            # Also update created_at to fix Bug #42 (cleanup loop time reference)
            await db.execute("""
                UPDATE ambulances 
                SET status = 'en_route',
                    lat = ?, lon = ?,
                    patient_severity = ?,
                    emergency_type = ?,
                    destination_hospital_id = ?,
                    eta_minutes = ?,
                    patient_vitals = ?,
                    created_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """, (
                req.ambulance_lat, req.ambulance_lon,
                severity.level.value,
                req.vitals.emergency_type.value,
                best.hospital.id,
                round(best.eta_minutes, 1),
                req.vitals.model_dump_json(),
                current_ambulance_id
            ))
            ambulance_id = current_ambulance_id

            # Check for multi-ambulance conflicts AFTER creating the record
            conflict_result = await check_and_resolve_conflicts(
                new_ambulance_id=ambulance_id,
                target_hospital_id=best.hospital.id,
                new_severity=severity,
                new_distance_km=best.distance_km,
                db=db
            )

            if conflict_result and isinstance(conflict_result, dict) and conflict_result.get("reroute_new"):
                # The new ambulance lost the priority battle — force it to the 2nd best hospital
                if len(ranked) > 1:
                    best = ranked[1]
                    await db.execute(
                        "UPDATE ambulances SET destination_hospital_id = ?, eta_minutes = ? WHERE id = ?",
                        (best.hospital.id, best.eta_minutes, ambulance_id)
                    )

            # Reserve bed at destination hospital only if critical
            bed_reserved = False
            if severity.level == SeverityLevel.CRITICAL:
                bed_reserved = await reserve_bed(best.hospital.id, db=db)

            # Log the routing event
            await log_event(
                "ambulance_routed",
                ambulance_id=ambulance_id,
                hospital_id=best.hospital.id,
                score=best.final_score,
                details=f"Severity: {severity.level.value} ({severity.score}), Dest: {best.hospital.name}, Distance: {best.distance_km}km, ETA: {best.eta_minutes}min",
                db=db
            )
    finally:
        await db.close()

    # Add to blockchain audit trail in the background
    background_tasks.add_task(add_block, {
        "event": "ROUTING_DECISION",
        "ambulance_id": ambulance_id,
        "severity": severity.level.value,
        "severity_score": severity.score,
        "hospital_id": best.hospital.id,
        "hospital_name": best.hospital.name,
        "final_score": best.final_score,
        "distance_km": best.distance_km,
        "eta_minutes": best.eta_minutes,
        "bed_reserved": bed_reserved,
        "vitals": {
            "heart_rate": req.vitals.heart_rate,
            "spo2": req.vitals.spo2,
            "systolic_bp": req.vitals.systolic_bp,
            "emergency_type": req.vitals.emergency_type.value,
            "age": req.vitals.age,
        },
    })
    
    # Add external Notifications if Critical Severity
    if severity.level == SeverityLevel.CRITICAL:
        background_tasks.add_task(
            dispatch_critical_alerts,
            hospital_id=best.hospital.id,
            hospital_name=best.hospital.name,
            patient_severity=severity.level.value,
            eta_minutes=best.eta_minutes
        )

    # Generate handoff alert and broadcast
    prep_instructions = get_prep_instructions(severity.level, req.vitals.emergency_type)
    handoff = HandoffAlert(
        ambulance_id=ambulance_id,
        hospital_id=best.hospital.id,
        hospital_name=best.hospital.name,
        severity=severity,
        vitals=req.vitals,
        eta_minutes=best.eta_minutes,
        prep_instructions=prep_instructions,
        bed_reserved=bed_reserved,
    )

    # Broadcast routing update
    await manager.broadcast({
        "type": "ambulance_routed",
        "ambulance_id": ambulance_id,
        "hospital_id": best.hospital.id,
        "hospital_name": best.hospital.name,
        "severity": severity.level.value,
        "eta_minutes": best.eta_minutes,
        "score": best.final_score,
    })

    # Broadcast handoff alert to hospital
    await manager.broadcast({
        "type": "handoff_alert",
        "handoff": handoff.model_dump(mode='json'),
    })

    if conflict_result:
        await manager.broadcast({
            "type": "alert",
            "message": f"⚡ Conflict resolved: ambulance redistributed from hospital #{best.hospital.id}",
            "details": conflict_result,
        })

    return RouteResponse(
        ambulance_id=ambulance_id,
        severity=severity,
        ranked_hospitals=ranked,
        recommended=best,
    )


# --- Hospital Acknowledge Handoff ---

@app.post("/api/hospitals/{hospital_id}/acknowledge")
async def acknowledge_handoff(hospital_id: int, token=Depends(require_hospital_admin)):
    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hospital_id,))
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Hospital not found")

        hospital = row_to_hospital(row)
    finally:
        await db.close()

    await manager.broadcast({
        "type": "handoff_acknowledged",
        "hospital_id": hospital_id,
        "hospital_name": hospital.name,
        "message": f"{hospital.name} has acknowledged incoming patient",
    })

    await log_event(
        "handoff_acknowledged",
        hospital_id=hospital_id,
        details=f"{hospital.name} acknowledged handoff",
    )

    return {"status": "acknowledged", "hospital_name": hospital.name}


# --- Confirm Acceptance (Lock Bed) ---

@app.post("/api/hospitals/{hospital_id}/accept/{ambulance_id}")
async def accept_patient(hospital_id: int, ambulance_id: int, token=Depends(require_hospital_admin)):
    # Verify the logged in user is actually allowed to accept for THIS hospital
    if token.role == "hospital_admin" and token.hospital_id != hospital_id:
        raise HTTPException(status_code=403, detail="Not authorized for this hospital")

    db = await get_db()
    try:
        # Strict IDOR check: Validate the ambulance exists, is routed specifically to THIS hospital, and is en_route.
        cursor = await db.execute("SELECT * FROM ambulances WHERE id = ?", (ambulance_id,))
        amb = await cursor.fetchone()
        
        if not amb:
            raise HTTPException(status_code=404, detail="Ambulance not found")
            
        if amb["destination_hospital_id"] != hospital_id:
            raise HTTPException(status_code=403, detail="Unauthorized: Ambulance is not routed to your hospital")
            
        if amb["status"] == "accepted":
            raise HTTPException(status_code=400, detail="Patient already accepted")
            
        if amb["status"] != "en_route":
            raise HTTPException(status_code=400, detail="Ambulance is not en route")

        # Wrap all writes in a single atomic transaction
        async with db.conn.transaction():
            # Update ambulance status to accepted
            await db.execute("UPDATE ambulances SET status = 'accepted' WHERE id = ?", (ambulance_id,))
            
            # Decrement soft_reserve, increment current_load — atomic
            await db.execute("""
                UPDATE hospitals 
                SET current_load = current_load + 1,
                    soft_reserve = CASE WHEN soft_reserve > 0 THEN soft_reserve - 1 ELSE soft_reserve END
                WHERE id = ?
            """, (hospital_id,))
        
        # Fetch updated hospital details (outside transaction, read-only)
        cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hospital_id,))
        row = await cursor.fetchone()
        hospital = row_to_hospital(row)
    finally:
        await db.close()
    
    # Invalidate hospital cache after acceptance
    await cache.invalidate_prefix("hospitals:")

    # Broadcast acceptance to network
    await manager.broadcast({
        "type": "patient_accepted",
        "hospital_id": hospital_id,
        "hospital_name": hospital.name,
        "ambulance_id": ambulance_id,
        "message": f"{hospital.name} has accepted and locked bed for patient #{ambulance_id}",
    })

    await log_event(
        "patient_accepted",
        ambulance_id=ambulance_id,
        hospital_id=hospital_id,
        details=f"Patient {ambulance_id} formally accepted by {hospital.name}",
    )

    return {"status": "accepted", "hospital_name": hospital.name, "ambulance_id": ambulance_id}


# --- Release Reserved Bed ---

@app.post("/api/hospitals/{hospital_id}/release-bed")
async def release_bed_endpoint(hospital_id: int, token=Depends(require_hospital_admin)):
    if token.role == "hospital_admin" and token.hospital_id != hospital_id:
        raise HTTPException(status_code=403, detail="Not authorized for this hospital")
    released = await release_bed(hospital_id)
    if not released:
        raise HTTPException(status_code=400, detail="No reserved beds to release")

    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hospital_id,))
        row = await cursor.fetchone()
        hospital = row_to_hospital(row)
    finally:
        await db.close()

    await manager.broadcast({
        "type": "bed_released",
        "hospital_id": hospital_id,
        "hospital_name": hospital.name,
        "icu_beds": hospital.icu_beds,
        "soft_reserve": hospital.soft_reserve,
    })

    return {"status": "released", "hospital": hospital.model_dump()}


# --- Discharge Patient ---

@app.post("/api/hospitals/{hospital_id}/discharge")
async def discharge_patient(hospital_id: int, token=Depends(require_hospital_admin)):
    if token.role == "hospital_admin" and token.hospital_id != hospital_id:
        raise HTTPException(status_code=403, detail="Not authorized")
        
    db = await get_db()
    try:
        cursor = await db.execute("SELECT current_load FROM hospitals WHERE id = ?", (hospital_id,))
        row = await cursor.fetchone()
        
        if row and row["current_load"] > 0:
            # Bug 51: Restore icu_beds when a patient cycles out
            await db.execute("UPDATE hospitals SET current_load = current_load - 1, icu_beds = icu_beds + 1 WHERE id = ?", (hospital_id,))
            await db.commit()
            await cache.invalidate_prefix("hospitals:")
            return {"status": "success", "message": "Patient discharged"}
        return {"status": "ignored", "message": "Load is already zero"}
    finally:
        await db.close()


# --- Complete Ambulance Run ---

@app.post("/api/ambulances/{ambulance_id}/complete")
async def complete_ambulance_run(ambulance_id: int, token=Depends(require_paramedic)):
    db = await get_db()
    try:
        # Verify this ambulance belongs to this paramedic
        cursor = await db.execute("SELECT * FROM ambulances WHERE id = ?", (ambulance_id,))
        amb = await cursor.fetchone()
        if not amb:
            raise HTTPException(status_code=404, detail="Ambulance not found")
            
        if token.role != "command_center":
            if not token.ambulance_id or amb["id"] != token.ambulance_id:
                raise HTTPException(
                    status_code=403, 
                    detail="You can only complete your own run"
                )

        # Bug #37: Release bed if critical run is completing
        # Ensured atomic execution with ambulance status update
        async with db.conn.transaction():
            if amb and amb["patient_severity"] == "critical" and amb["destination_hospital_id"]:
                await release_bed(amb["destination_hospital_id"], db=db)

            await db.execute("""
                UPDATE ambulances 
                SET status = 'idle', 
                    destination_hospital_id = NULL,
                    patient_severity = NULL,
                    emergency_type = NULL,
                    patient_vitals = '{}',
                    eta_minutes = 0
                WHERE id = ?
            """, (ambulance_id,))
        return {"status": "completed"}
    finally:
        await db.close()


# --- Ambulances ---

@app.get("/api/ambulances")
async def get_ambulances():
    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM ambulances")
        rows = await cursor.fetchall()
        return [dict(row) for row in rows]
    finally:
        await db.close()


@app.post("/api/ambulances")
async def create_ambulance(amb: AmbulanceCreate, token=Depends(require_paramedic)):
    db = await get_db()
    try:
        vitals_json = amb.patient_vitals.model_dump_json() if amb.patient_vitals else "{}"
        cursor = await db.execute(
            "INSERT INTO ambulances (name, lat, lon, patient_vitals) VALUES (?,?,?,?)",
            (amb.name, amb.lat, amb.lon, vitals_json),
        )
        await db.commit()
        return {"id": cursor.lastrowid, "name": amb.name}
    finally:
        await db.close()


@app.put("/api/ambulances/{ambulance_id}/position")
async def update_ambulance_position(
    ambulance_id: int, 
    pos: AmbulancePositionUpdate,
    token: TokenData = Depends(require_paramedic)
):
    if token.role != "command_center" and token.ambulance_id != ambulance_id:
        raise HTTPException(
            status_code=403,
            detail="You can only update your own ambulance"
        )
    db = await get_db()
    try:
        await db.execute(
            "UPDATE ambulances SET lat = ?, lon = ? WHERE id = ?",
            (pos.lat, pos.lon, ambulance_id),
        )
        await db.commit()

        await manager.broadcast({
            "type": "ambulance_position",
            "ambulance_id": ambulance_id,
            "lat": pos.lat,
            "lon": pos.lon,
        })

        return {"status": "updated"}
    finally:
        await db.close()


# --- Analytics ---

@app.get("/api/analytics", response_model=AnalyticsResponse)
async def get_analytics(token=Depends(require_command_center)):
    db = await get_db()
    try:
        # Total dispatches
        cursor = await db.execute("SELECT COUNT(*) as c FROM logs WHERE event_type = 'ambulance_routed'")
        total_dispatches = (await cursor.fetchone())["c"]

        # Total reroutes
        cursor = await db.execute("SELECT COUNT(*) as c FROM logs WHERE event_type IN ('ambulance_rerouted', 'conflict_resolved')")
        total_reroutes = (await cursor.fetchone())["c"]

        # Severity distribution
        cursor = await db.execute("SELECT patient_severity, COUNT(*) as c FROM ambulances GROUP BY patient_severity")
        sev_rows = await cursor.fetchall()
        severity_distribution = {row["patient_severity"]: row["c"] for row in sev_rows}

        # Hospital utilization
        cursor = await db.execute("SELECT id, name, current_load, max_capacity, icu_beds, total_icu_beds, soft_reserve FROM hospitals")
        h_rows = await cursor.fetchall()
        hospital_utilization = []
        for h in h_rows:
            cap = h["max_capacity"] if h["max_capacity"] > 0 else 1
            hospital_utilization.append({
                "id": h["id"],
                "name": h["name"],
                "load_pct": round(h["current_load"] / cap * 100, 1),
                "icu_available": h["icu_beds"],
                "icu_total": h["total_icu_beds"],
                "reserved": h["soft_reserve"],
            })

        # Average score
        cursor = await db.execute("SELECT AVG(score) as avg FROM logs WHERE score IS NOT NULL")
        avg_row = await cursor.fetchone()
        avg_score = round(avg_row["avg"], 4) if avg_row["avg"] else 0.0

        # Recent events count
        cursor = await db.execute("SELECT COUNT(*) as c FROM logs")
        recent_events = (await cursor.fetchone())["c"]

        reroute_rate = round(total_reroutes / max(total_dispatches, 1) * 100, 1)

        return AnalyticsResponse(
            total_dispatches=total_dispatches,
            total_reroutes=total_reroutes,
            severity_distribution=severity_distribution,
            hospital_utilization=hospital_utilization,
            avg_score=avg_score,
            reroute_rate=reroute_rate,
            recent_events=recent_events,
        )
    finally:
        await db.close()


# --- Audit Log ---

@app.get("/api/audit-log")
async def get_blockchain(limit: int = 50, token=Depends(require_command_center)):
    chain = await get_chain(limit)
    return chain


@app.get("/api/audit-log/verify")
async def verify_blockchain(token=Depends(require_command_center)):
    result = await verify_chain()
    return result


# --- Logs ---

@app.get("/api/logs")
async def get_logs(limit: int = 50, token=Depends(require_command_center)):
    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM logs ORDER BY id DESC LIMIT ?", (limit,))
        rows = await cursor.fetchall()
        return [dict(row) for row in rows]
    finally:
        await db.close()

# --- Settings ---

@app.get("/api/settings", response_model=SystemSettings)
async def get_settings(token=Depends(require_command_center)):
    db = await get_db()
    try:
        cursor = await db.execute("SELECT distance_weight, readiness_weight, severity_match_weight, max_routing_distance_km FROM settings WHERE id = 1")
        row = await cursor.fetchone()
        if not row:
            return SystemSettings()
        return dict(row)
    finally:
        await db.close()

@app.put("/api/settings", response_model=SystemSettings)
async def update_settings(settings: SystemSettings, token=Depends(require_command_center)):
    db = await get_db()
    try:
        await db.execute(
            """UPDATE settings 
               SET distance_weight = ?, readiness_weight = ?, severity_match_weight = ?, max_routing_distance_km = ? 
               WHERE id = 1""",
            (settings.distance_weight, settings.readiness_weight, settings.severity_match_weight, settings.max_routing_distance_km)
        )
        await db.commit()
        
        await manager.broadcast({
            "type": "alert",
            "message": "Engine configuration parameters updated."
        })
        
        return settings.model_dump()
    finally:
        await db.close()


# --- Simulation ---

@app.post("/api/simulate/overload/{hospital_id}")
async def simulate_overload(hospital_id: int, token=Depends(require_command_center)):
    """Simulate hospital overload (sets load to 98%, ICU to 0)."""
    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hospital_id,))
        row = await cursor.fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Hospital not found")

        max_cap = row["max_capacity"]
        overloaded = int(max_cap * 0.98)

        # Bug 52: Clear soft_reserve on simulated overload to prevent negative TOCTOU
        await db.execute(
            "UPDATE hospitals SET current_load = ?, icu_beds = 0, soft_reserve = 0 WHERE id = ?",
            (overloaded, hospital_id),
        )
        await db.commit()
        await cache.invalidate_prefix("hospitals:")

        cursor = await db.execute("SELECT * FROM hospitals WHERE id = ?", (hospital_id,))
        updated_row = await cursor.fetchone()
        hospital = row_to_hospital(updated_row)
    finally:
        await db.close()

    await log_event(
        "hospital_overloaded",
        hospital_id=hospital_id,
        details=f"{hospital.name} overloaded (simulated): load={overloaded}/{max_cap}, ICU=0",
    )

    await add_block({
        "event": "HOSPITAL_OVERLOADED",
        "hospital_id": hospital_id,
        "hospital_name": hospital.name,
        "load": overloaded,
        "max_capacity": max_cap,
        "trigger": "simulation",
    })

    await manager.broadcast({
        "type": "hospital_overloaded",
        "hospital": hospital.model_dump(),
    })

    # Auto-reroute ambulances heading to this hospital
    await check_and_reroute(hospital_id, hospital)

    return {"status": "overloaded", "hospital": hospital.model_dump()}


async def check_and_reroute(hospital_id: int, overloaded_hospital: HospitalInfo):
    """Reroute ambulances away from an overloaded hospital."""
    db = await get_db()
    try:
        cursor = await db.execute(
            "SELECT * FROM ambulances WHERE destination_hospital_id = ? AND status = 'en_route'",
            (hospital_id,),
        )
        affected = await cursor.fetchall()
        if not affected:
            return

        h_cursor = await db.execute("SELECT * FROM hospitals WHERE status = 'active' AND id != ?", (hospital_id,))
        h_rows = await h_cursor.fetchall()
        alt_hospitals = [row_to_hospital(r) for r in h_rows]

        for amb in affected:
            try:
                vitals_data = json.loads(amb["patient_vitals"])
                vitals = PatientVitals(**vitals_data)
            except Exception:
                continue

            severity = classify_severity(vitals)
            
            s_cursor = await db.execute("SELECT * FROM settings WHERE id = 1")
            s_row = await s_cursor.fetchone()
            weights = dict(s_row) if s_row else None
            
            ranked = await rank_hospitals(alt_hospitals, severity, vitals.emergency_type, amb["lat"], amb["lon"], weights=weights)

            if not ranked:
                continue

            new_best = ranked[0]

            bed_reserved = False
            async with db.conn.transaction():
                # Reserve bed at new hospital (overloaded already wiped ICU beds to 0, so no release needed)
                # Only reserve if critical (maintains consistency with route_ambulance)
                if severity.level == SeverityLevel.CRITICAL:
                    bed_reserved = await reserve_bed(new_best.hospital.id, db=db)

                await db.execute(
                    "UPDATE ambulances SET destination_hospital_id = ?, eta_minutes = ? WHERE id = ?",
                    (new_best.hospital.id, new_best.eta_minutes, amb["id"]),
                )

                await log_event(
                    "ambulance_rerouted",
                    ambulance_id=amb["id"],
                    hospital_id=new_best.hospital.id,
                    score=new_best.final_score,
                    details=f"Hospital #{hospital_id} overloaded. Rerouted to {new_best.hospital.name} ({new_best.distance_km}km, ETA: {new_best.eta_minutes}min)",
                    db=db
                )

            await add_block({
                "event": "AMBULANCE_REROUTED",
                "ambulance_id": amb["id"],
                "from_hospital": hospital_id,
                "from_hospital_name": overloaded_hospital.name,
                "to_hospital": new_best.hospital.id,
                "to_hospital_name": new_best.hospital.name,
                "reason": "hospital_overloaded",
                "new_score": new_best.final_score,
                "bed_reserved": bed_reserved,
            })

            await manager.broadcast_reroute(
                amb["id"], 
                hospital_id, 
                {"id": new_best.hospital.id, "name": new_best.hospital.name, "lat": new_best.hospital.lat, "lon": new_best.hospital.lon}, 
                "Hospital overloaded"
            )
    finally:
        await db.close()


@app.post("/api/simulate/reset")
async def simulate_reset(token=Depends(require_command_center)):
    """Reset all data and reseed."""
    db = await get_db()
    try:
        # Bug #34: Delete users and historical_patterns to avoid data corruption
        await db.execute("DELETE FROM users")
        await db.execute("DELETE FROM historical_patterns")
        await db.execute("DELETE FROM ambulances")
        await db.execute("DELETE FROM hospitals")
        await db.execute("DELETE FROM logs")
        await db.execute("DELETE FROM blockchain")
        await db.commit()
    finally:
        await db.close()

    await seed_data()
    await init_blockchain_table()
    return {"status": "reset"}


# --- WebSocket ---

from auth import SECRET_KEY, ALGORITHM
from jose import jwt, JWTError

@app.websocket("/ws/updates")
async def websocket_endpoint(websocket: WebSocket):
    # Retrieve token from cookies or query parameters
    token = None
    if "access_token" in websocket.cookies:
        token_str = websocket.cookies.get("access_token")
        if token_str.startswith("Bearer "):
            token = token_str[7:].strip()
            
    if not token and "token" in websocket.query_params:
        token = websocket.query_params.get("token")

    if not token:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Missing authentication token")
        return

    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        role: str = payload.get("role")
        if not username:
            raise JWTError()
    except JWTError:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION, reason="Invalid authentication token")
        return

    await manager.connect(websocket, role=role, hospital_id=payload.get("hospital_id"), username=username)
    try:
        while True:
            data = await websocket.receive_text()
            if data == "ping" or data == '{"type":"ping"}':
                await websocket.send_text('{"type":"pong"}')
            else:
                try:
                    message_data = json.loads(data)
                    msg_type = message_data.get("type")

                    # Handle location updates from paramedics
                    if msg_type == "location_update" and role == "paramedic":
                        secure_amb_id = payload.get("ambulance_id")
                        if secure_amb_id:
                            lat = message_data.get("lat")
                            lon = message_data.get("lon")
                            
                            # Bug #39: Validate lat/lon in WS message
                            if isinstance(lat, (int, float)) and isinstance(lon, (int, float)):
                                if -90 <= lat <= 90 and -180 <= lon <= 180:
                                    db = await get_db()
                                    try:
                                        await db.execute("UPDATE ambulances SET lat = ?, lon = ? WHERE id = ?", (lat, lon, secure_amb_id))
                                        await db.commit()
                                    finally:
                                        await db.close()
                                    log.debug(f"WS LatLon update for {secure_amb_id}: {lat},{lon}")
                                else:
                                    log.warning(f"Invalid WS coords range for {secure_amb_id}: {lat},{lon}")
                            else:
                                log.warning(f"Invalid WS coords types for {secure_amb_id}: {type(lat)},{type(lon)}")
                            
                            # 2. Broadcast to dashboards
                            await manager.broadcast({
                                "type": "location_update",
                                "ambulance_id": secure_amb_id,
                                "lat": lat,
                                "lon": lon,
                            })

                    # Handle ping messages in JSON format
                    elif msg_type == "ping":
                        await websocket.send_json({"type": "pong"})

                except json.JSONDecodeError:
                    pass
    except WebSocketDisconnect:
        manager.disconnect(websocket)
