"""
Cache management — the single source of mutable application state.

─────────────────────────────────────────────────────────────────────────────
FUNCTIONAL PROGRAMMING NOTE — handling state functionally
─────────────────────────────────────────────────────────────────────────────
Pure FP avoids mutable state entirely (using monads, STM, etc.).  In Python
we make a pragmatic compromise: we isolate ALL mutable state in this one
module, making mutations explicit and centralised.

The technique used here is *atomic reference replacement*:
  1. The current cache is an immutable CacheState snapshot.
  2. To "update" the cache we build a NEW CacheState from fresh DB data.
  3. We swap the module-level reference under an asyncio lock.

The rest of the application only reads CacheState objects (immutable), so
there is no risk of partial reads or data races on the individual fields.
─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

import asyncio
import logging

import asyncpg

from .database import fetch_telegram_groups, fetch_zones_of_interest
from .geometry import compute_publishable_group_ids, get_group_id_mapping # TODO: перенести з geometry
from .models import CacheState

logger = logging.getLogger(__name__)

# ── Module-level mutable state (the only such state in the entire service) ───
_current_cache: CacheState | None = None
_lock = asyncio.Lock()          # serialises concurrent refresh calls


# ── Readers (no lock needed — reading a reference is atomic in CPython) ──────

def get_cache() -> CacheState | None:
    """Return the current immutable cache snapshot (None until first load)."""
    return _current_cache


# ── Pure cache-builder (IO: reads DB, no writes) ─────────────────────────────

async def _build_cache(pool: asyncpg.Pool) -> CacheState:
    """
    Fetch fresh data from the DB and compute the derived publishable-group set.

    This function performs IO but returns a pure, immutable value.
    """
    groups = await fetch_telegram_groups(pool)
    zones = await fetch_zones_of_interest(pool)
    publishable_ids = compute_publishable_group_ids(groups, zones)
    group_id_mapping = get_group_id_mapping(groups)

    logger.info(
        f"Cache built: {len(groups)} groups | groups: {groups} | {len(zones)} zones | "
        f"{len(publishable_ids)} publishable group IDs: {sorted(publishable_ids)}"
    )
    return CacheState(
        groups=groups,
        zones=zones,
        publishable_group_ids=publishable_ids,
        group_id_mapping = group_id_mapping
    )


# ── Writers (acquire lock, replace reference) ─────────────────────────────────

async def refresh_cache(pool: asyncpg.Pool) -> CacheState:
    """Reload groups AND zones, recompute intersection set, swap cache."""
    global _current_cache
    new_cache = await _build_cache(pool)
    async with _lock:
        _current_cache = new_cache
    return new_cache


async def reset_groups_cache(pool: asyncpg.Pool) -> CacheState:
    """
    Reload only the telegram_groups table, recompute intersection set.

    Zones are taken from the existing cache (no extra DB round-trip).
    """
    global _current_cache
    async with _lock:
        existing = _current_cache

    groups = await fetch_telegram_groups(pool)
    zones = existing.zones if existing else ()
    publishable_ids = compute_publishable_group_ids(groups, zones)
    group_id_mapping = get_group_id_mapping(groups)
    logger.info(f'Group id mapping: {group_id_mapping}')

    new_cache = CacheState(
        groups=groups,
        zones=zones,
        publishable_group_ids=publishable_ids,
        group_id_mapping = group_id_mapping
    )
    async with _lock:
        _current_cache = new_cache

    logger.info(f"Groups cache reset: {len(groups)} groups reloaded")
    return new_cache


async def reset_zones_cache(pool: asyncpg.Pool) -> CacheState:
    """
    Reload only the zone_of_interest table, recompute intersection set.

    Groups are taken from the existing cache (no extra DB round-trip).
    """
    global _current_cache
    async with _lock:
        existing = _current_cache

    groups = existing.groups if existing else ()
    zones = await fetch_zones_of_interest(pool)
    publishable_ids = compute_publishable_group_ids(groups, zones)
    group_id_mapping = get_group_id_mapping(groups)

    new_cache = CacheState(
        groups=groups,
        zones=zones,
        publishable_group_ids=publishable_ids,
        group_id_mapping = group_id_mapping
    )
    async with _lock:
        _current_cache = new_cache

    logger.info(f"Zones cache reset: {len(zones)} zones reloaded")
    return new_cache
