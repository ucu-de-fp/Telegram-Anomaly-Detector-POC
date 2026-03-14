"""
Database access — IO layer (read-only).

This module is the *boundary* between the pure functional core and the
outside world (PostgreSQL / PostGIS).  All functions here perform IO and
therefore cannot be pure, but they are kept thin: they fetch rows and
immediately convert them into immutable domain objects, so the rest of the
application never sees raw DB rows.

PostGIS geometry columns are projected to WKT via ST_AsText() so that the
rest of the codebase only works with plain strings (no asyncpg codec magic
needed for geometry types).

Assumed schema (created by group-management-service):

  telegram_groups
    id                BIGINT / SERIAL PRIMARY KEY
    polygon           geometry(Polygon,4326)

  zone_of_interest
    id      BIGINT / SERIAL PRIMARY KEY
    zone geometry(Polygon,4326)
    active  BOOLEAN
"""
from __future__ import annotations

import logging

import asyncpg

from .config import Settings
from .models import TelegramGroup, ZoneOfInterest

logger = logging.getLogger(__name__)


async def create_db_pool(settings: Settings) -> asyncpg.Pool:
    """
    Create a connection pool.  The pool is reused for the entire lifetime
    of the service; never create a pool per request.
    """
    logger.info(
        f"Connecting to PostgreSQL at {settings.db_host}:{settings.db_port}"
        f" db={settings.db_name} user={settings.db_user}"
    )
    return await asyncpg.create_pool(
        dsn=settings.db_dsn,
        min_size=1,
        max_size=3,          # read-only; small pool is fine
        command_timeout=10,
    )


async def fetch_telegram_groups(
    pool: asyncpg.Pool,
) -> tuple[TelegramGroup, ...]:
    """
    Fetch all rows from telegram_groups.

    Groups without a polygon are skipped (they cannot participate in
    geo-filtering).
    """
    rows = await pool.fetch(
        """
        SELECT
            id,
            telegram_group_id,
            ST_AsText(polygon) AS polygon_wkt
        FROM telegram_groups
        WHERE polygon IS NOT NULL
        """
    )
    groups = tuple(
        TelegramGroup(
            id=row["id"],
            telegram_id=row["telegram_group_id"],
            polygon_wkt=row["polygon_wkt"],
        )
        for row in rows
    )
    logger.info(f"Fetched {len(groups)} telegram_groups from DB")
    return groups


async def fetch_zones_of_interest(
    pool: asyncpg.Pool,
) -> tuple[ZoneOfInterest, ...]:
    """
    Fetch all rows from zone_of_interest.

    We load *all* zones (active and inactive) so the cache can answer
    "which zones are active?" without a second DB round-trip.
    """
    rows = await pool.fetch(
        """
        SELECT
            id,
            ST_AsText(zone) AS polygon_wkt,
            active
        FROM zone_of_interest
        WHERE zone IS NOT NULL
        """
    )
    zones = tuple(
        ZoneOfInterest(
            id=row["id"],
            polygon_wkt=row["polygon_wkt"],
            active=bool(row["active"]),
        )
        for row in rows
    )
    logger.info(
        f"Fetched {len(zones)} zones_of_interest from DB "
        f"({sum(1 for z in zones if z.active)} active)"
    )
    return zones
