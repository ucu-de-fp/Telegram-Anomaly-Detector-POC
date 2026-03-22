from __future__ import annotations

from pydantic import BaseModel, ConfigDict, field_serializer
from dataclasses import asdict, dataclass
from datetime import datetime
from typing import Any

from .util.serialization import to_camel_case


@dataclass(frozen=True)
class TelegramGroup:
    """
    A Telegram group/channel tracked by the system.

    id - internal id of the telegram group
    telegram_group_id — the raw Telegram chat ID (string, may be negative).
    polygon_wkt       — geographic bounding area in WKT / EPSG:4326.
    """

    id: int
    telegram_id: str
    polygon_wkt: str


@dataclass(frozen=True)
class ZoneOfInterest:
    """
    A geographic zone defined by the admin.

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
    """

    message_id: int
    telegram_group_id: int
    text: str
    timestamp: datetime
    sender_id: str | None
    sender_name: str | None
    raw: dict[str, Any]

class DetectionMessage(BaseModel):
    message_id: int
    group_id: int
    content: str
    timestamp: datetime
    sender_id: str | None
    sender_name: str | None
    raw: dict[str, Any]

    model_config = ConfigDict(
        alias_generator=to_camel_case,
        populate_by_name=True,
    )

    @field_serializer("timestamp")
    def serialize_timestamp(self, value: datetime) -> str:
        return value.replace(tzinfo=None).isoformat()
    
@dataclass(frozen=True)
class CacheState:
    """
    An immutable snapshot of the application cache.
    """

    groups: tuple[TelegramGroup, ...]
    zones: tuple[ZoneOfInterest, ...]
    publishable_group_ids: frozenset[str]
    group_id_mapping: dict[str, int]
