"""
AEROVHYN — WebSocket Connection Manager v2
Per-role channels, heartbeat, reconnection tracking, and message queue.
"""

import json
import asyncio
import time
from collections import defaultdict
from fastapi import WebSocket
from logger import get_logger

log = get_logger("aerovhyn.ws")


class ConnectionManager:
    """Manages WebSocket connections with per-role channels and heartbeat."""

    def __init__(self):
        self.active_connections: list[dict] = []  # [{ws, role, hospital_id, username, connected_at}]
        self.heartbeat_task = None
        self._metrics = {
            "total_connects": 0,
            "total_disconnects": 0,
            "total_messages_sent": 0,
            "total_broadcasts": 0,
        }

    async def _heartbeat_loop(self):
        """30-second heartbeat to detect stale connections."""
        while True:
            await asyncio.sleep(30)
            dead = []
            for conn_info in self.active_connections.copy():
                try:
                    await conn_info["ws"].send_json({"type": "ping"})
                except Exception:
                    dead.append(conn_info)
            for d in dead:
                self.disconnect(d["ws"])

    async def connect(self, websocket: WebSocket, role: str = None, hospital_id: int = None, username: str = None):
        """Accept a WebSocket connection and register with metadata."""
        await websocket.accept()
        conn_info = {
            "ws": websocket,
            "role": role,
            "hospital_id": hospital_id,
            "username": username,
            "connected_at": time.time(),
        }
        self.active_connections.append(conn_info)
        self._metrics["total_connects"] += 1

        if self.heartbeat_task is None:
            self.heartbeat_task = asyncio.create_task(self._heartbeat_loop())

        # Log connection
        role_str = role or "unknown"
        log.info(f"WS connected", extra={"role": role_str, "username": username, "total": len(self.active_connections)})

    def disconnect(self, websocket: WebSocket):
        """Remove a WebSocket connection."""
        for conn_info in self.active_connections:
            if conn_info["ws"] is websocket:
                self.active_connections.remove(conn_info)
                self._metrics["total_disconnects"] += 1
                log.info(f"WS disconnected", extra={"role": conn_info.get('role', '?'), "username": conn_info.get('username', '?'), "total": len(self.active_connections)})
                break

    async def broadcast(self, message: dict):
        """Send a JSON message to ALL connected clients."""
        dead = []
        self._metrics["total_broadcasts"] += 1

        for conn_info in self.active_connections.copy():
            try:
                await conn_info["ws"].send_json(message)
                self._metrics["total_messages_sent"] += 1
            except Exception:
                dead.append(conn_info)

        for d in dead:
            self.disconnect(d["ws"])

    async def broadcast_to_role(self, role: str, message: dict):
        """Send message only to clients with a specific role."""
        dead = []
        for conn_info in self.active_connections.copy():
            if conn_info.get("role") == role:
                try:
                    await conn_info["ws"].send_json(message)
                    self._metrics["total_messages_sent"] += 1
                except Exception:
                    dead.append(conn_info)

        for d in dead:
            self.disconnect(d["ws"])

    async def broadcast_to_hospital(self, hospital_id: int, message: dict):
        """Send message only to hospital admin clients for a specific hospital."""
        dead = []
        for conn_info in self.active_connections.copy():
            if conn_info.get("hospital_id") == hospital_id:
                try:
                    await conn_info["ws"].send_json(message)
                    self._metrics["total_messages_sent"] += 1
                except Exception:
                    dead.append(conn_info)

        for d in dead:
            self.disconnect(d["ws"])

    async def broadcast_hospital_update(self, hospital_id: int, data: dict):
        await self.broadcast({
            "type": "hospital_update",
            "hospital_id": hospital_id,
            "data": data,
        })

    async def broadcast_ambulance_update(self, ambulance_id: int, data: dict):
        await self.broadcast({
            "type": "ambulance_update",
            "ambulance_id": ambulance_id,
            "data": data,
        })

    async def broadcast_reroute(self, ambulance_id: int, old_hospital_id: int, new_hospital: dict, reason: str):
        await self.broadcast({
            "type": "reroute",
            "ambulance_id": ambulance_id,
            "old_hospital_id": old_hospital_id,
            "to_hospital": new_hospital["id"],
            "to_hospital_name": new_hospital["name"],
            "to_hospital_lat": new_hospital["lat"],
            "to_hospital_lon": new_hospital["lon"],
            "reason": reason,
        })

    async def broadcast_alert(self, message: str, level: str = "info"):
        await self.broadcast({
            "type": "alert",
            "message": message,
            "level": level,
        })

    def get_stats(self) -> dict:
        """Return connection statistics."""
        role_counts = defaultdict(int)
        for conn in self.active_connections:
            role_counts[conn.get("role", "unknown")] += 1

        return {
            "active_connections": len(self.active_connections),
            "connections_by_role": dict(role_counts),
            **self._metrics,
        }


manager = ConnectionManager()
