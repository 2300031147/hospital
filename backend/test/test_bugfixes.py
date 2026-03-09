"""
test_bugfixes.py — Tests for bugs found during white box testing.
"""
import sys
import os
import pytest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from cache import AsyncTTLCache


# --- Test 1: Verify bed_reserved variable is properly scoped ---
# This is a static analysis test: we parse the check_and_reroute function
# to ensure bed_reserved is defined before use.

def test_bed_reserved_defined_before_use():
    """Bug fix: bed_reserved must be defined before it is referenced in add_block()."""
    import ast
    import inspect
    from main import check_and_reroute

    source = inspect.getsource(check_and_reroute)
    # Dedent the source to parse it properly
    import textwrap
    source = textwrap.dedent(source)
    tree = ast.parse(source)

    # Walk through the AST and find all Name nodes where id == 'bed_reserved'
    assignments = []
    usages = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Name) and node.id == 'bed_reserved':
            if isinstance(node.ctx, (ast.Store,)):
                assignments.append(node.lineno)
            elif isinstance(node.ctx, (ast.Load,)):
                usages.append(node.lineno)

    # bed_reserved must be assigned at least once
    assert len(assignments) >= 1, "bed_reserved is never assigned in check_and_reroute"
    # Every usage must come after at least one assignment
    first_assignment = min(assignments)
    for usage_line in usages:
        assert usage_line >= first_assignment, (
            f"bed_reserved used at line {usage_line} before first assignment at line {first_assignment}"
        )


# --- Test 2: Verify hospital update uses exclude_unset (not exclude_none) ---

def test_hospital_update_uses_exclude_unset():
    """Bug fix: update_hospital should use exclude_unset=True for consistency with update_user."""
    import inspect
    from main import update_hospital

    source = inspect.getsource(update_hospital)
    assert "exclude_unset=True" in source, "update_hospital should use model_dump(exclude_unset=True)"
    assert "exclude_none=True" not in source, "update_hospital should NOT use model_dump(exclude_none=True)"


# --- Test 3: Verify WebSocket token parsing uses slice not split ---

def test_websocket_token_uses_slice():
    """Bug fix: WebSocket token extraction should use [7:].strip() not split(' ')[1]."""
    import inspect
    from main import websocket_endpoint

    source = inspect.getsource(websocket_endpoint)
    # Should NOT use split-based token extraction
    assert 'split(" ")[1]' not in source, "WebSocket token parsing should not use split(' ')[1]"
    assert "split(\" \")[1]" not in source, "WebSocket token parsing should not use split(' ')[1]"
    # Should use slice-based extraction
    assert "[7:]" in source, "WebSocket token parsing should use [7:].strip()"


# --- Test 4: Cache logging on failures ---

@pytest.mark.asyncio
async def test_cache_set_get_memory():
    """Verify basic cache operations work with in-memory fallback."""
    c = AsyncTTLCache(default_ttl=10)
    await c.set("test_key", {"value": 42})
    result = await c.get("test_key")
    assert result == {"value": 42}


@pytest.mark.asyncio
async def test_cache_delete_memory():
    """Verify cache delete works."""
    c = AsyncTTLCache(default_ttl=10)
    await c.set("del_key", "hello")
    await c.delete("del_key")
    result = await c.get("del_key")
    assert result is None


@pytest.mark.asyncio
async def test_cache_invalidate_prefix():
    """Verify prefix invalidation works."""
    c = AsyncTTLCache(default_ttl=10)
    await c.set("hospitals:1", "h1")
    await c.set("hospitals:2", "h2")
    await c.set("other:1", "o1")
    await c.invalidate_prefix("hospitals:")
    assert await c.get("hospitals:1") is None
    assert await c.get("hospitals:2") is None
    assert await c.get("other:1") == "o1"


@pytest.mark.asyncio
async def test_cache_expired_entries():
    """Verify expired entries are not returned."""
    import time
    c = AsyncTTLCache(default_ttl=1)
    await c.set("expire_key", "data", ttl=0)
    # TTL=0 means it expires immediately (time.time() + 0 = now, which is already past)
    # Small sleep to ensure expiry
    import asyncio
    await asyncio.sleep(0.01)
    result = await c.get("expire_key")
    assert result is None
