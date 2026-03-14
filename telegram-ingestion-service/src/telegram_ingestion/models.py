"""
Immutable domain models — the functional core's data layer.

─────────────────────────────────────────────────────────────────────────────
FUNCTIONAL PROGRAMMING NOTE
─────────────────────────────────────────────────────────────────────────────
In functional programming, data and behaviour are separated:
  • These dataclasses carry ONLY data — no methods that perform IO.
  • frozen=True makes every instance immutable; Python raises FrozenInstanceError
    on any attempt to mutate a field after construction.
  • Immutability enables safe sharing across async tasks without locks.
  • Geometry is stored as WKT strings (hashable, serialisable, comparable)
    rather than Shapely objects (which are mutable and not hashable by default).

The Shapely Polygon is built lazily by the geometry module when needed.
─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any


# @dataclass(frozen=True)
@dataclass # TODO: return frozen=True
class TelegramGroup:
    """
    A Telegram group/channel tracked by the system.

    telegram_group_id — the raw Telegram chat ID (string, may be negative).
    polygon_wkt       — geographic bounding area in WKT / EPSG:4326.
    """

    id: int
    telegram_id: str
    polygon_wkt: str


@dataclass(frozen=True)
class ZoneOfInterest:
    """
    A geographic zone defined by the operators.

    Messages are forwarded only if their source group's polygon overlaps
    at least one *active* zone.
    """

    id: int
    polygon_wkt: str
    active: bool


@dataclass(frozen=True)
class TelegramMessage:
    """
    A single ingested Telegram message, ready for filtering and publishing.

    group_telegram_id must match TelegramGroup.telegram_group_id for the
    geo-filter to work correctly.
    """

    message_id: int
    group: TelegramGroup
    # group_telegram_id: int
    text: str
    timestamp: datetime
    sender_id: str | None
    sender_name: str | None
    raw: dict[str, Any]


@dataclass(frozen=True)
class CacheState:
    """
    An immutable snapshot of the application cache.

    ─────────────────────────────────────────────────────────────────────────
    FUNCTIONAL PROGRAMMING NOTE
    ─────────────────────────────────────────────────────────────────────────
    Instead of mutating a cache object in place, we *replace* the entire
    CacheState with a new instance.  The cache module holds a reference to
    the "current" snapshot; swapping that reference is the only mutation.

    publishable_group_ids is a pre-computed frozenset so that the hot path
    (one lookup per incoming message) is O(1) rather than O(groups × zones).
    ─────────────────────────────────────────────────────────────────────────
    """

    groups: tuple[TelegramGroup, ...]
    zones: tuple[ZoneOfInterest, ...]
    # Pre-computed result of geometry.compute_publishable_group_ids()
    publishable_group_ids: frozenset[str]
    group_id_mapping: dict[str, int]
