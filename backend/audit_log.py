"""
AEROVHYN — Tamper-Evident Audit Log
SHA-256 hash chain for immutable routing decision records.
Each routing decision is a "block" linked to the previous one by hash.
"""

import hashlib
import json
import time
from datetime import datetime, timezone

from database import get_db


async def init_blockchain_table():
    """Create blockchain table if it doesn't exist."""
    db = await get_db()
    try:
        await db.execute("""
            CREATE TABLE IF NOT EXISTS blockchain (
                idx INTEGER PRIMARY KEY,
                timestamp TEXT NOT NULL,
                data TEXT NOT NULL,
                prev_hash TEXT NOT NULL,
                hash TEXT NOT NULL,
                nonce INTEGER DEFAULT 0
            )
        """)
        await db.commit()

        # Check if genesis block exists
        cursor = await db.execute("SELECT COUNT(*) FROM blockchain")
        row = await cursor.fetchone()
        if row[0] == 0:
            await _add_genesis_block(db)
    finally:
        await db.close()


async def _add_genesis_block(db):
    """Create the genesis (first) block."""
    block_data = {
        "event": "SYSTEM_GENESIS",
        "message": "AEROVHYN Tamper-Evident Audit Log initialized",
        "system_version": "2.0.0",
    }
    timestamp = datetime.now(timezone.utc).isoformat()
    data_str = json.dumps(block_data, sort_keys=True)
    prev_hash = "0" * 64
    block_str = f"0|{timestamp}|{data_str}|{prev_hash}"
    block_hash = hashlib.sha256(block_str.encode()).hexdigest()

    await db.execute(
        "INSERT INTO blockchain (idx, timestamp, data, prev_hash, hash) VALUES (?, ?, ?, ?, ?)",
        (0, timestamp, data_str, prev_hash, block_hash),
    )
    await db.commit()


async def add_block(data: dict) -> dict:
    """
    Add a new block to the chain.
    Returns the new block as a dict.
    """
    db = await get_db()
    try:
        # Serialize chain writes with a Postgres advisory lock to prevent
        # concurrent inserts from creating duplicate idx values.
        lock_id = hash("audit_chain") % (2**63 - 1)
        await db.execute("SELECT pg_advisory_lock($1)", (lock_id,))

        # Get the last block
        cursor = await db.execute("SELECT * FROM blockchain ORDER BY idx DESC LIMIT 1")
        last_row = await cursor.fetchone()

        new_idx = last_row["idx"] + 1
        timestamp = datetime.now(timezone.utc).isoformat()
        data_str = json.dumps(data, sort_keys=True)
        prev_hash = last_row["hash"]

        # Compute hash
        block_str = f"{new_idx}|{timestamp}|{data_str}|{prev_hash}"
        block_hash = hashlib.sha256(block_str.encode()).hexdigest()

        await db.execute(
            "INSERT INTO blockchain (idx, timestamp, data, prev_hash, hash) VALUES (?, ?, ?, ?, ?)",
            (new_idx, timestamp, data_str, prev_hash, block_hash),
        )
        await db.commit()

        return {
            "index": new_idx,
            "timestamp": timestamp,
            "data": data,
            "prev_hash": prev_hash,
            "hash": block_hash,
        }
    finally:
        lock_id = hash("audit_chain") % (2**63 - 1)
        try:
            await db.execute("SELECT pg_advisory_unlock($1)", (lock_id,))
        except Exception:
            pass
        await db.close()


async def get_chain(limit: int = 50) -> list[dict]:
    """Get the blockchain (most recent blocks first)."""
    db = await get_db()
    try:
        cursor = await db.execute(
            "SELECT * FROM blockchain ORDER BY idx DESC LIMIT ?", (limit,)
        )
        rows = await cursor.fetchall()
        chain = []
        for row in rows:
            chain.append({
                "index": row["idx"],
                "timestamp": row["timestamp"],
                "data": json.loads(row["data"]),
                "prev_hash": row["prev_hash"],
                "hash": row["hash"],
            })
        return chain
    finally:
        await db.close()


async def verify_chain() -> dict:
    """
    Verify the integrity of the entire blockchain.
    Returns verification result with details.
    """
    db = await get_db()
    try:
        cursor = await db.execute("SELECT * FROM blockchain ORDER BY idx ASC")
        rows = await cursor.fetchall()

        if not rows:
            return {"valid": False, "error": "Chain is empty", "blocks_checked": 0}

        blocks_checked = 0
        for i, row in enumerate(rows):
            idx = row["idx"]
            timestamp = row["timestamp"]
            data_str = row["data"]
            prev_hash = row["prev_hash"]
            stored_hash = row["hash"]

            # Recompute hash
            block_str = f"{idx}|{timestamp}|{data_str}|{prev_hash}"
            computed_hash = hashlib.sha256(block_str.encode()).hexdigest()

            if computed_hash != stored_hash:
                return {
                    "valid": False,
                    "error": f"Hash mismatch at block {idx}",
                    "block_index": idx,
                    "expected": computed_hash,
                    "stored": stored_hash,
                    "blocks_checked": blocks_checked,
                }

            # Check prev_hash linkage (skip genesis)
            if i > 0:
                prev_row = rows[i - 1]
                if prev_hash != prev_row["hash"]:
                    return {
                        "valid": False,
                        "error": f"Chain broken at block {idx}: prev_hash doesn't match block {prev_row['idx']}",
                        "block_index": idx,
                        "blocks_checked": blocks_checked,
                    }

            blocks_checked += 1

        return {
            "valid": True,
            "blocks_checked": blocks_checked,
            "chain_length": len(rows),
            "latest_hash": rows[-1]["hash"],
            "message": "✅ Audit Log integrity verified — all hashes valid",
        }
    finally:
        await db.close()
