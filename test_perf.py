import asyncio
import time

class PostgresDBWrapper:
    def __init__(self, pool, conn):
        self.pool = pool
        self.conn = conn

    @staticmethod
    def _to_pg(query: str, params: tuple) -> tuple[str, tuple]:
        pg_query = ""
        in_quotes = False
        chunks = []
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

        pg_query = pg_query.replace("INSERT OR IGNORE INTO", "INSERT INTO")
        pg_query = pg_query.replace("INSERT OR REPLACE INTO", "INSERT INTO")

        return pg_query, params

    async def execute(self, query: str, params: tuple = ()):
        pg_query, params = self._to_pg(query, params)
        await self.conn.execute(pg_query, *params)

    async def executemany(self, query: str, parameters: list[tuple]):
        pg_query, _ = self._to_pg(query, ())
        await self.conn.executemany(pg_query, parameters)

class MockConn:
    async def execute(self, q, *p):
        pass
    async def fetch(self, q, *p):
        return []
    async def executemany(self, q, p):
        pass

async def test():
    conn = MockConn()
    db = PostgresDBWrapper(None, conn)

    start = time.perf_counter()
    h_id = 1
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
    end = time.perf_counter()
    print(f"execute loop: {end - start:.4f}s")

    # executemany
    start = time.perf_counter()
    params = []
    for day in range(7):
        for hour in range(24):
            base_load = 0.6
            if 18 <= hour <= 23: base_load += 0.2
            if day >= 5: base_load += 0.1
            base_turnover = 0.05
            params.append((h_id, day, hour, min(base_load, 1.0), base_turnover))

    await db.executemany(
        "INSERT INTO historical_patterns (hospital_id, day_of_week, hour_of_day, avg_load, avg_turnover_rate) VALUES (?, ?, ?, ?, ?)",
        params
    )
    end = time.perf_counter()
    print(f"executemany: {end - start:.4f}s")

asyncio.run(test())
