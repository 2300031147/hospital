"""
AEROVHYN — Database Layer
SQLite async setup with hospitals, ambulances, and logs tables.
"""

import aiosqlite
import json           # BUG-1 FIX: was missing, caused NameError in seed_data()
import asyncpg
import os
import re
import asyncio
from passlib.context import CryptContext

DB_PATH_DEFAULT = os.path.join(os.path.dirname(__file__), "aerovhyn.db")
DATABASE_URL = os.getenv("DATABASE_URL")

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def hash_password(password: str) -> str:
    return pwd_context.hash(password)

def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)

class CursorWrapper:
    def __init__(self, rows=None, lastrowid=None):
        self.rows = rows or []
        self.lastrowid = lastrowid
        self._idx = 0
        self.rowcount = len(rows) if rows else 0  # Bug #8 fix: rowcount needed by delete/update endpoints

    async def fetchone(self):
        if self._idx < len(self.rows):
            row = self.rows[self._idx]
            self._idx += 1
            return row
        return None

    async def fetchall(self):
        return self.rows

class PostgresDBWrapper:
    def __init__(self, pool, conn):
        self.pool = pool
        self.conn = conn

    async def execute(self, query: str, params: tuple = ()):
        pg_query = ""
        in_quotes = False
        chunks = []
        current_chunk = ""
        i = 0
        param_idx = 1
        while i < len(query):
            if query[i] == "'":
                in_quotes = not in_quotes
                current_chunk += query[i]
            elif query[i] == "?" and not in_quotes:
                chunks.append(current_chunk)
                chunks.append(f"${param_idx}")
                param_idx += 1
                current_chunk = ""
            else:
                current_chunk += query[i]
            i += 1
        chunks.append(current_chunk)
        pg_query = "".join(chunks)

        upper_query = pg_query.strip().upper()
        if upper_query.startswith(("CREATE", "ALTER", "DROP", "TRUNCATE", "PRAGMA")):
            await self.conn.execute(pg_query, *params)
            return CursorWrapper()

        is_insert = upper_query.startswith("INSERT")
        if is_insert and "RETURNING" not in upper_query:
            pg_query += " RETURNING id"

        try:
            rows = await self.conn.fetch(pg_query, *params)
            lastrowid = None
            if is_insert and rows and 'id' in rows[0]:
                lastrowid = rows[0]['id']
            cursor = CursorWrapper(rows=rows, lastrowid=lastrowid)
            cursor.rowcount = len(rows)
            return cursor
        except Exception as e:
            if "does not exist" in str(e) or "RETURNING" in str(e):
                pg_query = pg_query.replace(" RETURNING id", "")
                result = await self.conn.execute(pg_query, *params)
                cursor = CursorWrapper()
                # asyncpg execute returns a string like "UPDATE 1" — parse rowcount
                try:
                    cursor.rowcount = int(str(result).split()[-1])
                except Exception:
                    cursor.rowcount = 0
                return cursor
            raise

    async def executescript(self, script: str):
        await self.conn.execute(script)

    async def commit(self):
        pass

    async def close(self):
        await self.pool.release(self.conn)

_pg_pool = None
_pool_lock = asyncio.Lock()

async def get_db():
    global _pg_pool
    if DATABASE_URL and DATABASE_URL.startswith("postgres"):
        if not _pg_pool:
            async with _pool_lock:
                if not _pg_pool:
                    _pg_pool = await asyncpg.create_pool(DATABASE_URL.replace("postgresql://", "postgres://"))
        conn = await _pg_pool.acquire()
        return PostgresDBWrapper(_pg_pool, conn)
    else:
        db_path = os.getenv("AEROVHYN_DB_PATH", DB_PATH_DEFAULT)
        db = await aiosqlite.connect(db_path)
        db.row_factory = aiosqlite.Row
        return db

async def init_db():
    """Create tables if they don't exist."""
    db = await get_db()
    try:
        if not DATABASE_URL or not DATABASE_URL.startswith("postgres"):
            await db.execute("PRAGMA journal_mode=WAL;")
            await db.executescript("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    full_name TEXT NOT NULL,
                    role TEXT NOT NULL DEFAULT 'paramedic',
                    ambulance_id INTEGER,
                    hospital_id INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS hospitals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    icu_beds INTEGER DEFAULT 0,
                    total_icu_beds INTEGER DEFAULT 10,
                    soft_reserve INTEGER DEFAULT 0,
                    ventilators INTEGER DEFAULT 0,
                    total_ventilators INTEGER DEFAULT 5,
                    specialists TEXT DEFAULT '[]',
                    current_load INTEGER DEFAULT 0,
                    max_capacity INTEGER DEFAULT 100,
                    equipment_score REAL DEFAULT 0.8,
                    status TEXT DEFAULT 'active',
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS ambulances (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT DEFAULT 'AMB-001',
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    patient_severity TEXT DEFAULT 'unknown',
                    destination_hospital_id INTEGER,
                    emergency_type TEXT,
                    status TEXT DEFAULT 'idle',
                    patient_vitals TEXT DEFAULT '{}',
                    eta_minutes REAL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (destination_hospital_id) REFERENCES hospitals(id)
                );

                CREATE TABLE IF NOT EXISTS logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    event_type TEXT NOT NULL,
                    ambulance_id INTEGER,
                    hospital_selected_id INTEGER,
                    score REAL,
                    details TEXT DEFAULT '',
                    FOREIGN KEY (ambulance_id) REFERENCES ambulances(id),
                    FOREIGN KEY (hospital_selected_id) REFERENCES hospitals(id)
                );

                CREATE TABLE IF NOT EXISTS blockchain (
                    idx INTEGER PRIMARY KEY,
                    timestamp TEXT NOT NULL,
                    data TEXT NOT NULL,
                    prev_hash TEXT NOT NULL,
                    hash TEXT NOT NULL,
                    nonce INTEGER DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS historical_patterns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hospital_id INTEGER NOT NULL,
                    day_of_week INTEGER NOT NULL,
                    hour_of_day INTEGER NOT NULL,
                    avg_load REAL NOT NULL,
                    avg_turnover_rate REAL DEFAULT 0.05,
                    UNIQUE(hospital_id, day_of_week, hour_of_day),
                    FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
                );

                CREATE TABLE IF NOT EXISTS settings (
                    id INTEGER PRIMARY KEY,
                    distance_weight REAL DEFAULT 0.2,
                    readiness_weight REAL DEFAULT 0.5,
                    severity_match_weight REAL DEFAULT 0.3,
                    max_routing_distance_km REAL DEFAULT 30.0
                );
            """)

            await db.execute("""
                INSERT OR IGNORE INTO settings (id, distance_weight, readiness_weight, severity_match_weight, max_routing_distance_km)
                VALUES (1, 0.2, 0.5, 0.3, 30.0)
            """)
            await db.commit()
        else:
            try:
                await db.execute("""
                    INSERT INTO settings (id, distance_weight, readiness_weight, severity_match_weight, max_routing_distance_km)
                    VALUES (1, 0.2, 0.5, 0.3, 30.0)
                    ON CONFLICT(id) DO NOTHING
                """)
            except Exception:
                pass
    finally:
        await db.close()


async def seed_data():
    """Seed default users and 8 realistic hospitals in a metro area (Hyderabad-inspired coordinates)."""
    db = await get_db()
    try:
        cursor = await db.execute("SELECT COUNT(*) FROM users")
        row = await cursor.fetchone()
        if row[0] == 0:
            # BUG-8 FIX: Seed ambulances FIRST so integer FK references are valid
            ambulances = [
                ("AMB-001", 17.4239, 78.4483),
                ("AMB-002", 17.4156, 78.4347),
                ("AMB-003", 17.4401, 78.4983),
            ]
            for name, lat, lon in ambulances:
                await db.execute(
                    "INSERT INTO ambulances (name, lat, lon) VALUES (?,?,?)", (name, lat, lon)
                )
            await db.commit()

            # BUG-5 FIX: ambulance_id is now INTEGER — use numeric IDs (1, 2, 3)
            default_users = [
                ("admin",      hash_password("admin123"),  "System Administrator", "command_center", None, None),
                ("hosp1",      hash_password("hosp123"),   "Apollo Admin",          "hospital_admin", None, 1),
                ("hosp2",      hash_password("hosp123"),   "KIMS Admin",            "hospital_admin", None, 2),
                ("paramedic1", hash_password("rescue123"), "Ravi Kumar",            "paramedic",      1,    None),
                ("driver1",    hash_password("drive123"),  "Suresh Reddy",          "paramedic",      2,    None),
                ("medic01",    hash_password("medic123"),  "Priya Sharma",          "paramedic",      3,    None),
            ]
            for u in default_users:
                await db.execute(
                    "INSERT INTO users (username, password_hash, full_name, role, ambulance_id, hospital_id) VALUES (?,?,?,?,?,?)",
                    u,
                )
            await db.commit()

        # --- Seed hospitals ---
        cursor = await db.execute("SELECT COUNT(*) FROM hospitals")
        row = await cursor.fetchone()
        if row[0] > 0:
            return

        hospitals = [
            {
                "name": "Apollo Emergency Hospital",
                "lat": 17.4239, "lon": 78.4483,
                "icu_beds": 8, "total_icu_beds": 12,
                "ventilators": 5, "total_ventilators": 8,
                "specialists": json.dumps(["cardiology", "neurology", "trauma"]),
                "current_load": 45, "max_capacity": 120,
                "equipment_score": 0.95, "status": "active",
            },
            {
                "name": "KIMS Heart Center",
                "lat": 17.4156, "lon": 78.4347,
                "icu_beds": 6, "total_icu_beds": 10,
                "ventilators": 4, "total_ventilators": 6,
                "specialists": json.dumps(["cardiology", "pulmonology"]),
                "current_load": 62, "max_capacity": 100,
                "equipment_score": 0.90, "status": "active",
            },
            {
                "name": "Yashoda Super Specialty",
                "lat": 17.4401, "lon": 78.4983,
                "icu_beds": 10, "total_icu_beds": 15,
                "ventilators": 7, "total_ventilators": 10,
                "specialists": json.dumps(["cardiology", "orthopedics", "neurology", "trauma"]),
                "current_load": 30, "max_capacity": 150,
                "equipment_score": 0.92, "status": "active",
            },
            {
                "name": "Care Hospitals",
                "lat": 17.4485, "lon": 78.3908,
                "icu_beds": 4, "total_icu_beds": 8,
                "ventilators": 3, "total_ventilators": 5,
                "specialists": json.dumps(["trauma", "orthopedics"]),
                "current_load": 78, "max_capacity": 90,
                "equipment_score": 0.85, "status": "active",
            },
            {
                "name": "Continental General Hospital",
                "lat": 17.4350, "lon": 78.4600,
                "icu_beds": 3, "total_icu_beds": 6,
                "ventilators": 2, "total_ventilators": 4,
                "specialists": json.dumps(["general", "pulmonology"]),
                "current_load": 55, "max_capacity": 80,
                "equipment_score": 0.78, "status": "active",
            },
            {
                "name": "Sunshine Trauma Center",
                "lat": 17.4100, "lon": 78.4750,
                "icu_beds": 7, "total_icu_beds": 10,
                "ventilators": 5, "total_ventilators": 7,
                "specialists": json.dumps(["trauma", "neurology", "orthopedics"]),
                "current_load": 40, "max_capacity": 110,
                "equipment_score": 0.88, "status": "active",
            },
            {
                "name": "Medicover Emergency Wing",
                "lat": 17.4600, "lon": 78.4200,
                "icu_beds": 5, "total_icu_beds": 8,
                "ventilators": 3, "total_ventilators": 5,
                "specialists": json.dumps(["cardiology", "general"]),
                "current_load": 70, "max_capacity": 95,
                "equipment_score": 0.82, "status": "active",
            },
            {
                "name": "Global Hospitals",
                "lat": 17.4000, "lon": 78.4400,
                "icu_beds": 12, "total_icu_beds": 18,
                "ventilators": 9, "total_ventilators": 12,
                "specialists": json.dumps(["cardiology", "neurology", "trauma", "pulmonology", "orthopedics"]),
                "current_load": 25, "max_capacity": 200,
                "equipment_score": 0.97, "status": "active",
            },
        ]

        for h in hospitals:
            try:
                await db.execute(
                    """INSERT INTO hospitals (name, lat, lon, icu_beds, total_icu_beds,
                       ventilators, total_ventilators, specialists, current_load,
                       max_capacity, equipment_score, status)
                       VALUES (?, ?, ?, ?, ?,
                       ?, ?, ?, ?,
                       ?, ?, ?)""",
                    (h['name'], h['lat'], h['lon'], h['icu_beds'], h['total_icu_beds'],
                     h['ventilators'], h['total_ventilators'], h['specialists'], h['current_load'],
                     h['max_capacity'], h['equipment_score'], h['status'])
                )
            except Exception as e:
                from logger import get_logger
                db_log = get_logger("aerovhyn.db")
                db_log.warning("hospital_seed_failed", extra={"name": h["name"], "error": str(e)})

        # Seed historical patterns
        cursor = await db.execute("SELECT id FROM hospitals")
        h_ids = await cursor.fetchall()
        for idx in h_ids:
            h_id = idx["id"]
            for day in range(7):
                for hour in range(24):
                    base_load = 0.6
                    if 18 <= hour <= 23: base_load += 0.2
                    if day >= 5: base_load += 0.1
                    base_turnover = 0.05
                    try:
                        await db.execute(
                            "INSERT OR IGNORE INTO historical_patterns (hospital_id, day_of_week, hour_of_day, avg_load, avg_turnover_rate) VALUES (?, ?, ?, ?, ?)",
                            (h_id, day, hour, min(base_load, 1.0), base_turnover)
                        )
                    except Exception:
                        pass

        await db.commit()
    finally:
        await db.close()
