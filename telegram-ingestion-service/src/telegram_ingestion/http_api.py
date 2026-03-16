"""
HTTP API — cache management endpoints.

Provides a small REST API so the front-end can force-reload
groups and zones of interest without restarting the service.

Endpoints:
  GET  /health              — liveness probe
  GET  /cache/status        — current cache statistics
  POST /cache/reset         — reload groups + zones, recompute filter set
  POST /cache/reset/groups  — reload only telegram_groups
  POST /cache/reset/zones   — reload only zone_of_interest

─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

import logging

import asyncpg
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from . import cache as cache_module

logger = logging.getLogger(__name__)

app = FastAPI(
    title="Telegram Ingestion Service — Cache API",
    version="0.1.0",
    description=(
        "Manages the in-process cache of Telegram groups and zones of interest. "
        "Call /cache/reset after modifying groups or zones in the database."
    ),
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


def _require_pool(request: Request) -> asyncpg.Pool:
    """Extract the DB pool from app.state, raising 503 if not yet ready."""
    pool: asyncpg.Pool | None = getattr(request.app.state, "db_pool", None)
    if pool is None:
        raise HTTPException(status_code=503, detail="Database pool not initialised yet")
    return pool


@app.get("/health", tags=["ops"])
async def health_check() -> dict:
    """Liveness endpoint."""
    cache = cache_module.get_cache()
    return {
        "status": "ok",
        "cache_loaded": cache is not None,
    }


@app.get("/cache/status", tags=["cache"])
async def cache_status() -> dict:
    """Return current cache statistics"""
    cache = cache_module.get_cache()
    if cache is None:
        return {"status": "not_loaded"}
    return {
        "status": "loaded",
        "groups_count": len(cache.groups),
        "zones_count": len(cache.zones),
        "active_zones_count": sum(1 for z in cache.zones if z.active),
        "publishable_groups_count": len(cache.publishable_group_ids),
        "publishable_group_ids": sorted(cache.publishable_group_ids),
    }


@app.post("/cache/reset", tags=["cache"])
async def reset_all_caches(request: Request) -> dict:
    """
    Reload data from the database, then recompute the cached state.
    """
    pool = _require_pool(request)
    new_cache = await cache_module.refresh_cache(pool)
    logger.info("Full cache reset triggered via HTTP API")
    return {
        "status": "reset",
        "groups_count": len(new_cache.groups),
        "zones_count": len(new_cache.zones),
        "publishable_groups_count": len(new_cache.publishable_group_ids),
        "publishable_group_ids": sorted(new_cache.publishable_group_ids),
    }


@app.post("/cache/reset/groups", tags=["cache"])
async def reset_groups(request: Request) -> dict:
    """
    Reload only telegram_groups, recompute filter set against existing zones.
    """
    pool = _require_pool(request)
    new_cache = await cache_module.reset_groups_cache(pool)
    logger.info("Groups cache reset triggered via HTTP API")
    return {
        "status": "reset",
        "groups_count": len(new_cache.groups),
        "publishable_groups_count": len(new_cache.publishable_group_ids),
    }


@app.post("/cache/reset/zones", tags=["cache"])
async def reset_zones(request: Request) -> dict:
    """
    Reload only zone_of_interest, recompute filter set against existing groups.
    """
    pool = _require_pool(request)
    new_cache = await cache_module.reset_zones_cache(pool)
    logger.info("Zones cache reset triggered via HTTP API")
    return {
        "status": "reset",
        "zones_count": len(new_cache.zones),
        "active_zones_count": sum(1 for z in new_cache.zones if z.active),
        "publishable_groups_count": len(new_cache.publishable_group_ids),
    }
