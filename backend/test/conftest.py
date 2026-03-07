"""
conftest.py — Shared pytest config that resets the slowapi rate limit storage between tests.
"""
import os
import sys
import pytest
import asyncio

# Set env vars BEFORE any imports
os.environ.setdefault("AEROVHYN_JWT_SECRET", "temporary_secret_for_tests_" + ("1" * 32))
os.environ.setdefault("CORS_ORIGINS", "http://localhost:5173")
os.environ.setdefault("AEROVHYN_DB_PATH", "test_aerovhyn.db")
if "DATABASE_URL" in os.environ:
    del os.environ["DATABASE_URL"]

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))


@pytest.fixture(autouse=True)
def reset_rate_limiter():
    """Reset slowapi in-memory rate limit storage between every test."""
    from main import app
    import main as main_module
    main_module.failed_logins.clear()
    limiter = getattr(app.state, "limiter", None)
    if limiter and hasattr(limiter, "_storage"):
        try:
            limiter._storage.reset()
        except Exception:
            pass
    yield
