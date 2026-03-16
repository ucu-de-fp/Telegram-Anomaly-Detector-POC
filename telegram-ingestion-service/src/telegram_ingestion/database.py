"""
Database access — IO layer.

Schema is created by the admin-api-service
"""
from __future__ import annotations

import logging

import asyncpg

from .config import Settings
from .models import TelegramGroup, ZoneOfInterest

logger = logging.getLogger(__name__)


async def create_db_pool(settings: Settings) -> asyncpg.Pool:
    logger.info(
        f"Connecting to PostgreSQL at {settings.db_host}:{settings.db_port}"
        f" db={settings.db_name} user={settings.db_user}"
    )
    return await asyncpg.create_pool(
        dsn=settings.db_dsn,
        min_size=1,
        max_size=3,
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
