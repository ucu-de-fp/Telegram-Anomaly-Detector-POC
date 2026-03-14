"""
Pure geometric computation functions.

─────────────────────────────────────────────────────────────────────────────
FUNCTIONAL PROGRAMMING NOTE
─────────────────────────────────────────────────────────────────────────────
Every function in this module is *pure*:
  • Given the same inputs it always returns the same output.
  • It performs no IO, reads no global state, and has no side effects.
  • This makes each function trivially testable in isolation.

The heavy lifting is done by Shapely 2.x, which wraps GEOS — a battle-tested
C++ geometry library.  We keep all Shapely objects local to each call so they
are never shared across threads/tasks.
─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

from shapely import wkt as shapely_wkt
from shapely.geometry import Polygon

from .models import TelegramGroup, ZoneOfInterest


# ── Low-level primitives ──────────────────────────────────────────────────────

def parse_polygon(wkt_string: str) -> Polygon:
    """Parse a WKT string into a Shapely Polygon.  Pure."""
    return shapely_wkt.loads(wkt_string)


def polygons_intersect(wkt_a: str, wkt_b: str) -> bool:
    """
    Return True if the two WKT-encoded polygons share any point.

    Uses Shapely's DE-9IM `intersects` predicate, which returns True even
    for touch-only (boundary) intersections.  Change to `overlaps` if you
    only want interior overlap.

    Pure function — referentially transparent.
    """
    return parse_polygon(wkt_a).intersects(parse_polygon(wkt_b))


# ── Domain-level predicates ───────────────────────────────────────────────────

def group_intersects_any_active_zone(
    group: TelegramGroup,
    zones: tuple[ZoneOfInterest, ...],
) -> bool:
    """
    Pure predicate: does this group's area overlap at least one active zone?

    Uses a generator with any() for short-circuit evaluation — we stop
    as soon as the first intersection is found.
    """
    return any(
        polygons_intersect(group.polygon_wkt, zone.polygon_wkt)
        for zone in zones
        if zone.active
    )


# ── Batch computation (called when rebuilding the cache) ─────────────────────

def compute_publishable_group_ids(
    groups: tuple[TelegramGroup, ...],
    zones: tuple[ZoneOfInterest, ...],
) -> frozenset[str]:
    """
    Pure function: return the set of telegram_group_ids that overlap with
    at least one active zone.

    ─────────────────────────────────────────────────────────────────────────
    FUNCTIONAL PROGRAMMING NOTE
    ─────────────────────────────────────────────────────────────────────────
    This is an example of *derived data*: instead of storing boolean flags
    on each group object, we compute a new, immutable data structure that
    encodes the answer to "which groups should we publish from?".

    The result is a frozenset (immutable set) stored in the CacheState.
    Every incoming message does a single O(1) membership test against it.

    Because this function is pure it can be called at any time without risk,
    memoised, or parallelised.
    ─────────────────────────────────────────────────────────────────────────
    """
    return frozenset(
        group.telegram_id
        for group in groups
        if group_intersects_any_active_zone(group, zones)
    )

def get_group_id_mapping(
    groups: tuple[TelegramGroup, ...]
) -> dict[str, int]:
    return {group.telegram_id: group.id for group in groups}