"""
Geometry computation functions
"""
from __future__ import annotations

from shapely import wkt as shapely_wkt
from shapely.geometry import Polygon

from .models import TelegramGroup, ZoneOfInterest


def parse_polygon(wkt_string: str) -> Polygon:
    """Parse a WKT string into a Shapely Polygon."""
    return shapely_wkt.loads(wkt_string)


def polygons_intersect(wkt_a: str, wkt_b: str) -> bool:
    """
    Return True if the two WKT-encoded polygons share any point.
    """
    return parse_polygon(wkt_a).intersects(parse_polygon(wkt_b))


def group_intersects_any_active_zone(
    group: TelegramGroup,
    zones: tuple[ZoneOfInterest, ...],
) -> bool:
    """
    Returns True if this group's area overlap at least one active zone

    Uses a generator with any() for short-circuit evaluation — we stop
    as soon as the first intersection is found.
    """
    return any(
        polygons_intersect(group.polygon_wkt, zone.polygon_wkt)
        for zone in zones
        if zone.active
    )


def compute_publishable_group_ids(
    groups: tuple[TelegramGroup, ...],
    zones: tuple[ZoneOfInterest, ...],
) -> frozenset[str]:
    """
    Returns the set of telegram_group_ids that overlap with
    at least one active zone.
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