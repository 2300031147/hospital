"""
AEROVHYN — In-Memory TTL Cache
Redis-compatible interface with TTL expiration.
Provides caching for frequently accessed data like hospital lists and analytics.
"""

import time
import asyncio
from typing import Any, Optional
from functools import wraps


class TTLCache:
    """Simple in-memory cache with TTL (time-to-live) expiration."""

    def __init__(self, default_ttl: int = 30):
        self._store: dict[str, tuple[Any, float]] = {}
        self._default_ttl = default_ttl
        self._cleanup_task = None

    def get(self, key: str) -> Optional[Any]:
        """Get value if key exists and hasn't expired."""
        if key in self._store:
            value, expires_at = self._store[key]
            if time.time() < expires_at:
                return value
            else:
                del self._store[key]
        return None

    def set(self, key: str, value: Any, ttl: Optional[int] = None):
        """Set value with TTL (seconds). Uses default TTL if not specified."""
        expiry = time.time() + (ttl if ttl is not None else self._default_ttl)
        self._store[key] = (value, expiry)

    def delete(self, key: str):
        """Delete a key from the cache."""
        self._store.pop(key, None)

    def invalidate_prefix(self, prefix: str):
        """Delete all keys matching a prefix."""
        keys_to_delete = [k for k in self._store if k.startswith(prefix)]
        for k in keys_to_delete:
            del self._store[k]

    def clear(self):
        """Clear all cached data."""
        self._store.clear()

    def stats(self) -> dict:
        """Return cache statistics."""
        now = time.time()
        total = len(self._store)
        expired = sum(1 for _, (_, exp) in self._store.items() if now >= exp)
        return {
            "total_keys": total,
            "expired_keys": expired,
            "active_keys": total - expired,
        }

    async def cleanup_expired(self):
        """Background task to periodically clean expired entries."""
        while True:
            await asyncio.sleep(60)
            now = time.time()
            expired_keys = [k for k, (_, exp) in self._store.items() if now >= exp]
            for k in expired_keys:
                del self._store[k]
            if expired_keys:
                print(f"[CACHE] Cleaned {len(expired_keys)} expired entries")

    def start_cleanup(self):
        """Start background cleanup task."""
        if self._cleanup_task is None:
            self._cleanup_task = asyncio.create_task(self.cleanup_expired())


# Singleton cache instance
cache = TTLCache(default_ttl=15)


def cached(key_template: str, ttl: int = 15):
    """
    Decorator to cache async function results.
    
    Usage:
        @cached("hospitals:all", ttl=10)
        async def get_hospitals():
            ...
    """
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            cache_key = key_template
            
            # Check cache first
            result = cache.get(cache_key)
            if result is not None:
                return result

            # Execute function and cache result
            result = await func(*args, **kwargs)
            cache.set(cache_key, result, ttl)
            return result

        # Expose invalidation helper
        wrapper.invalidate = lambda: cache.delete(key_template)
        return wrapper
    return decorator
