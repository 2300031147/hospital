"""
AEROVHYN — Database Layer
PostgreSQL only via asyncpg connection pool.
Schema is managed by Alembic migrations — run `alembic upgrade head` on first deploy.
"""

import json
import asyncpg
import os
import asyncio
from passlib.context import CryptContext

DATABASE_URL = os.getenv("DATABASE_URL")

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


class CursorWrapper:
    """
    Thin wrapper that gives asyncpg rows a cursor-like interface
    so call-sites throughout main.py remain unchanged.
    """
    def __init__(self, rows=None, lastrowid=None):
        self.rows = rows or []
        self.lastrowid = lastrowid
        self._idx = 0
        self.rowcount = len(self.rows)

    async def fetchone(self):
        if self._idx < len(self.rows):
            row = self.rows[self._idx]
            self._idx += 1
            return row
        return None

    async def fetchall(self):
        return self.rows


class PostgresDBWrapper:
    """
    Wraps a single asyncpg connection (acquired from pool) and
    exposes a cursor-compatible interface used throughout main.py.
    """

    def __init__(self, pool: asyncpg.Pool, conn: asyncpg.Connection):
        self.pool = pool
        self.conn = conn

    # ── Internal helper ──────────────────────────────────────────────────────
    @staticmethod
    def _to_pg(query: str, params: tuple) -> tuple[str, tuple]:
        """
        Rewrite SQLite-style positional `?` placeholders to asyncpg `$N`
        style, skipping occurrences inside single-quoted string literals.
        Also converts INSERT OR IGNORE / INSERT OR REPLACE for Postgres.
        """
        pg_query = ""
        in_quotes = False
        chunks: list[str] = []
        current_chunk = ""
        param_idx = 1

        for ch in query:
            if ch == "'":
                in_quotes = not in_quotes
                current_chunk += ch
            elif ch == "?" and not in_quotes:
                chunks.append(current_chunk)
                chunks.append(f"${param_idx}")
                param_idx += 1
                current_chunk = ""
            else:
                current_chunk += ch
        chunks.append(current_chunk)
        pg_query = "".join(chunks)

        # SQLite-isms → Postgres equivalents
        pg_query = pg_query.replace("INSERT OR IGNORE INTO", "INSERT INTO")
        pg_query = pg_query.replace("INSERT OR REPLACE INTO", "INSERT INTO")

        return pg_query, params

    # ── Public interface ─────────────────────────────────────────────────────
    async def execute(self, query: str, params: tuple = ()):
        pg_query, params = self._to_pg(query, params)
        upper = pg_query.strip().upper()

        # DDL (CREATE, ALTER, DROP …) — no RETURNING, no rows expected
        if upper.startswith(("CREATE", "ALTER", "DROP", "TRUNCATE")):
            await self.conn.execute(pg_query, *params)
            return CursorWrapper()

        is_insert = upper.startswith("INSERT")
        needs_returning = is_insert and "RETURNING" not in upper

        # For INSERT OR IGNORE rewrites add ON CONFLICT DO NOTHING
        if "INSERT OR IGNORE" in query.upper() or "INSERT OR IGNORE" in pg_query.upper():
            # Already rewritten above; append conflict clause
            if "ON CONFLICT" not in upper:
                pg_query += " ON CONFLICT DO NOTHING"

        if needs_returning and "ON CONFLICT DO NOTHING" not in pg_query.upper():
            pg_query += " RETURNING id"

        try:
            rows = await self.conn.fetch(pg_query, *params)
            lastrowid = rows[0]["id"] if (is_insert and rows and "id" in rows[0]) else None
            cursor = CursorWrapper(rows=list(rows), lastrowid=lastrowid)
            return cursor
        except Exception as e:
            err = str(e)
            # RETURNING clashed with a table that uses a different PK name (e.g. blockchain.idx)
            if "RETURNING" in err or "does not exist" in err:
                pg_query = pg_query.replace(" RETURNING id", "")
                result = await self.conn.execute(pg_query, *params)
                cursor = CursorWrapper()
                try:
                    cursor.rowcount = int(str(result).split()[-1])
                except Exception:
                    cursor.rowcount = 0
                return cursor
            raise

    async def executescript(self, script: str):
        """Execute a block of SQL statements (DDL only, no params)."""
        await self.conn.execute(script)

    async def commit(self):
        """
        asyncpg auto-commits in non-transaction mode.
        Inside an implicit transaction block, issue an explicit COMMIT.
        """
        try:
            await self.conn.execute("COMMIT")
        except Exception:
            pass  # No active transaction — safe to ignore

    async def close(self):
        await self.pool.release(self.conn)


# ── Pool singleton ────────────────────────────────────────────────────────────
_pg_pool: asyncpg.Pool | None = None
_pool_lock = asyncio.Lock()


async def get_db() -> PostgresDBWrapper:
    """Acquire a connection from the asyncpg pool. Caller MUST call db.close()."""
    global _pg_pool
    if not DATABASE_URL:
        raise RuntimeError(
            "DATABASE_URL environment variable is not set. "
            "Set it to a PostgreSQL connection string, e.g. "
            "postgresql://user:password@host:5432/dbname"
        )

    if not _pg_pool:
        async with _pool_lock:
            if not _pg_pool:  # double-checked locking
                dsn = DATABASE_URL.replace("postgresql://", "postgres://")
                _pg_pool = await asyncpg.create_pool(dsn, min_size=2, max_size=20)

    conn = await _pg_pool.acquire()
    return PostgresDBWrapper(_pg_pool, conn)


# ── Schema bootstrap (Postgres) ───────────────────────────────────────────────
async def init_db():
    """
    Ensure the settings row exists.
    Full schema is managed by Alembic — run `alembic upgrade head` before this.
    """
    db = await get_db()
    try:
        await db.execute("""
            INSERT INTO settings (id, distance_weight, readiness_weight, severity_match_weight, max_routing_distance_km)
            VALUES (1, 0.2, 0.5, 0.3, 30.0)
            ON CONFLICT(id) DO NOTHING
        """)
        await db.commit()
    except Exception:
        pass  # Table may not exist yet if Alembic hasn't run — fail gracefully
    finally:
        await db.close()


# ── Seed data ─────────────────────────────────────────────────────────────────
async def seed_data():
    """Seed default users and 8 realistic hospitals (Hyderabad-inspired coordinates)."""
    db = await get_db()
    try:
        cursor = await db.execute("SELECT COUNT(*) FROM users")
        row = await cursor.fetchone()
        if row[0] == 0:
            # Seed ambulances FIRST — users hold integer FKs to these rows
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

        # Seed hospitals (idempotent)
        cursor = await db.execute("SELECT COUNT(*) FROM hospitals")
        row = await cursor.fetchone()
        if row[0] > 0:
            return

        hospitals = [
            {"name": "Apollo Emergency Hospital",   "lat": 17.4239, "lon": 78.4483, "icu_beds": 8,  "total_icu_beds": 12, "ventilators": 5, "total_ventilators": 8,  "specialists": json.dumps(["cardiology", "neurology", "trauma"]),                               "current_load": 45, "max_capacity": 120, "equipment_score": 0.95, "status": "active"},
            {"name": "KIMS Heart Center",           "lat": 17.4156, "lon": 78.4347, "icu_beds": 6,  "total_icu_beds": 10, "ventilators": 4, "total_ventilators": 6,  "specialists": json.dumps(["cardiology", "pulmonology"]),                                       "current_load": 62, "max_capacity": 100, "equipment_score": 0.90, "status": "active"},
            {"name": "Yashoda Super Specialty",     "lat": 17.4401, "lon": 78.4983, "icu_beds": 10, "total_icu_beds": 15, "ventilators": 7, "total_ventilators": 10, "specialists": json.dumps(["cardiology", "orthopedics", "neurology", "trauma"]),                  "current_load": 30, "max_capacity": 150, "equipment_score": 0.92, "status": "active"},
            {"name": "Care Hospitals",              "lat": 17.4485, "lon": 78.3908, "icu_beds": 4,  "total_icu_beds": 8,  "ventilators": 3, "total_ventilators": 5,  "specialists": json.dumps(["trauma", "orthopedics"]),                                           "current_load": 78, "max_capacity": 90,  "equipment_score": 0.85, "status": "active"},
            {"name": "Continental General Hospital","lat": 17.4350, "lon": 78.4600, "icu_beds": 3,  "total_icu_beds": 6,  "ventilators": 2, "total_ventilators": 4,  "specialists": json.dumps(["general", "pulmonology"]),                                          "current_load": 55, "max_capacity": 80,  "equipment_score": 0.78, "status": "active"},
            {"name": "Sunshine Trauma Center",      "lat": 17.4100, "lon": 78.4750, "icu_beds": 7,  "total_icu_beds": 10, "ventilators": 5, "total_ventilators": 7,  "specialists": json.dumps(["trauma", "neurology", "orthopedics"]),                              "current_load": 40, "max_capacity": 110, "equipment_score": 0.88, "status": "active"},
            {"name": "Medicover Emergency Wing",    "lat": 17.4600, "lon": 78.4200, "icu_beds": 5,  "total_icu_beds": 8,  "ventilators": 3, "total_ventilators": 5,  "specialists": json.dumps(["cardiology", "general"]),                                           "current_load": 70, "max_capacity": 95,  "equipment_score": 0.82, "status": "active"},
            {"name": "Global Hospitals",            "lat": 17.4000, "lon": 78.4400, "icu_beds": 12, "total_icu_beds": 18, "ventilators": 9, "total_ventilators": 12, "specialists": json.dumps(["cardiology", "neurology", "trauma", "pulmonology", "orthopedics"]), "current_load": 25, "max_capacity": 200, "equipment_score": 0.97, "status": "active"},
        ]

        for h in hospitals:
            try:
                await db.execute(
                    """INSERT INTO hospitals (name, lat, lon, icu_beds, total_icu_beds,
                       ventilators, total_ventilators, specialists, current_load,
                       max_capacity, equipment_score, status)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (h["name"], h["lat"], h["lon"], h["icu_beds"], h["total_icu_beds"],
                     h["ventilators"], h["total_ventilators"], h["specialists"], h["current_load"],
                     h["max_capacity"], h["equipment_score"], h["status"])
                )
            except Exception as e:
                from logger import get_logger
                get_logger("aerovhyn.db").warning("hospital_seed_failed", extra={"name": h["name"], "error": str(e)})

        # Seed historical patterns (ON CONFLICT DO NOTHING for idempotency)
        cursor = await db.execute("SELECT id FROM hospitals")
        h_ids = await cursor.fetchall()
        for idx in h_ids:
            h_id = idx["id"]
            for day in range(7):
                for hour in range(24):
                    base_load = 0.6
                    if 18 <= hour <= 23:
                        base_load += 0.2
                    if day >= 5:
                        base_load += 0.1
                    try:
                        await db.execute(
                            """INSERT INTO historical_patterns
                               (hospital_id, day_of_week, hour_of_day, avg_load, avg_turnover_rate)
                               VALUES (?, ?, ?, ?, ?)
                               ON CONFLICT (hospital_id, day_of_week, hour_of_day) DO NOTHING""",
                            (h_id, day, hour, min(base_load, 1.0), 0.05)
                        )
                    except Exception:
                        pass

        await db.commit()
    finally:
        await db.close()
