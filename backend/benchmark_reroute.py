import asyncio
import time
import json
import inspect

async def run_benchmark():
    # Let's write a targeted benchmark focused on the specific method structure
    class DummyCursor:
        def __init__(self, rows):
            self.rows = rows
            self.idx = 0

        async def fetchall(self):
            return self.rows

        async def fetchone(self):
            if self.idx < len(self.rows):
                val = self.rows[self.idx]
                self.idx += 1
                return val
            return None

    class DummyDB:
        def __init__(self):
            self.settings_queries = 0

        async def execute(self, query, *args):
            if "SELECT * FROM settings" in query:
                self.settings_queries += 1
                return DummyCursor([{"distance_weight": 1.0, "load_weight": 1.0, "acuity_weight": 1.0}])
            return DummyCursor([])

    # Mock DB
    db = DummyDB()

    # Generate 10,000 ambulances
    affected = []
    for i in range(10000):
        affected.append({
            "patient_vitals": json.dumps({"heart_rate": 80, "blood_pressure": "120/80", "respiratory_rate": 16, "temperature": 37.0, "oxygen_saturation": 98, "emergency_type": "trauma"}),
            "lat": 0.0,
            "lon": 0.0
        })

    # The original loop structure
    start_time = time.perf_counter()
    for amb in affected:
        s_cursor = await db.execute("SELECT * FROM settings WHERE id = 1")
        s_row = await s_cursor.fetchone()
        weights = dict(s_row) if s_row else None

    end_time = time.perf_counter()
    baseline_time = end_time - start_time
    baseline_queries = db.settings_queries

    print(f"BASELINE: {baseline_time:.4f}s with {baseline_queries} queries to settings table")

    # The optimized loop structure
    db.settings_queries = 0
    start_time = time.perf_counter()

    s_cursor = await db.execute("SELECT * FROM settings WHERE id = 1")
    s_row = await s_cursor.fetchone()
    weights = dict(s_row) if s_row else None

    for amb in affected:
        # loop body avoiding DB query
        pass

    end_time = time.perf_counter()
    optimized_time = end_time - start_time
    optimized_queries = db.settings_queries

    print(f"OPTIMIZED: {optimized_time:.4f}s with {optimized_queries} queries to settings table")

    improvement = baseline_time - optimized_time
    percent = (improvement / baseline_time) * 100 if baseline_time > 0 else 0
    print(f"IMPROVEMENT: {improvement:.4f}s ({percent:.2f}%)")

if __name__ == '__main__':
    asyncio.run(run_benchmark())
