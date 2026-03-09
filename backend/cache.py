"""
AEROVHYN — In-Memory / Redis Async Cache
Provides caching for frequently accessed data like hospital lists and analytics.
Automatically uses Redis if REDIS_URL is presented, otherwise an asyncio in-memory fallback.
"""

import time
import asyncio
import os
import json
import logging
from typing import Any, Optional
from functools import wraps

log = logging.getLogger("aerovhyn.cache")

REDIS_URL = os.getenv("REDIS_URL")

try:
    import redis.asyncio as redis
except ImportError:
    redis = None

class AsyncTTLCache:
    """Async cache interface backing into either in-memory dict or Redis."""
    
    def __init__(self, default_ttl: int = 30):
        self._default_ttl = default_ttl
        self._store = {}
        self._cleanup_task = None
        self._redis = None
        if REDIS_URL and redis:
            self._redis = redis.from_url(REDIS_URL, decode_responses=True)

    async def get(self, key: str) -> Optional[Any]:
        """Get value if key exists and hasn't expired."""
        if self._redis:
            try:
                val = await self._redis.get(key)
                if val:
                    return json.loads(val)
                return None
            except Exception as e:
                # Fallback to memory on Redis error for Bug #36
                log.warning("Redis GET failed, falling back to memory", extra={"error": str(e), "key": key})
        else:
            if key in self._store:
                value, expires_at = self._store[key]
                if time.time() < expires_at:
                    return value
                else:
                    del self._store[key]
            return None

    async def set(self, key: str, value: Any, ttl: Optional[int] = None):
        """Set value with TTL (seconds)."""
        expiry_sec = ttl if ttl is not None else self._default_ttl
        if self._redis:
            try:
                await self._redis.set(key, json.dumps(value), ex=expiry_sec)
                # We still set to memory as fallback/backup
            except Exception as e:
                log.warning("Redis SET failed, falling back to memory", extra={"error": str(e), "key": key})
        else:
            expiry = time.time() + expiry_sec
            self._store[key] = (value, expiry)

    async def delete(self, key: str):
        """Delete a key from the cache."""
        if self._redis:
            try:
                await self._redis.delete(key)
            except Exception as e:
                log.warning("Redis DELETE failed", extra={"error": str(e), "key": key})
        else:
            self._store.pop(key, None)

    async def invalidate_prefix(self, prefix: str):
        """Delete all keys matching a prefix."""
        if self._redis:
            try:
                keys = await self._redis.keys(f"{prefix}*")
                if keys:
                    await self._redis.delete(*keys)
            except Exception as e:
                log.warning("Redis invalidate_prefix failed", extra={"error": str(e), "prefix": prefix})
        else:
            keys_to_delete = [k for k in self._store if k.startswith(prefix)]
            for k in keys_to_delete:
                del self._store[k]

    async def clear(self):
        """Clear all cached data."""
        if self._redis:
            await self._redis.flushdb()
        else:
            self._store.clear()

    async def stats(self) -> dict:
        """Return cache statistics."""
        if self._redis:
            # Bug #62: Only request the chunk of Redis Info we need to save bandwidth and CPU
            info = await self._redis.info("memory")
            dbsize = await self._redis.dbsize()
            return {
                "total_keys": dbsize,
                "redis_used_memory_human": info.get('used_memory_human', '0B'),
                "backend": "redis"
            }
        else:
            now = time.time()
            total = len(self._store)
            expired = sum(1 for _, (_, exp) in self._store.items() if now >= exp)
            return {
                "total_keys": total,
                "expired_keys": expired,
                "active_keys": total - expired,
                "backend": "memory"
            }

    async def cleanup_expired(self):
        """Background task to periodically clean expired entries (only for memory cache)."""
        if self._redis:
            return # Redis natively handles expiration
            
        while True:
            await asyncio.sleep(60)
            now = time.time()
            expired_keys = [k for k, (_, exp) in self._store.items() if now >= exp]
            for k in expired_keys:
                del self._store[k]

    def start_cleanup(self):
        """Start background cleanup task."""
        if not self._redis and self._cleanup_task is None:
            self._cleanup_task = asyncio.create_task(self.cleanup_expired())


# Singleton cache instance
cache = AsyncTTLCache(default_ttl=15)


def cached(key_template: str, ttl: int = 15):
    """
    Decorator to cache async function results.
    """
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            # Try to format the key if it has placeholders, using kwargs
            try:
                cache_key = key_template.format(**kwargs) if kwargs else key_template
            except KeyError:
                cache_key = key_template

            # Check cache first
            result = await cache.get(cache_key)
            if result is not None:
                return result

            # Execute function and cache result
            result = await func(*args, **kwargs)
            await cache.set(cache_key, result, ttl)
            return result
        return wrapper
    return decorator
