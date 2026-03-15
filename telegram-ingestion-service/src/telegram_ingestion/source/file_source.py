"""
File-based message source — used when MESSAGE_SOURCE is set to "FILE".

Source file can be created by exporting the chat history from telegram:
Telegram Desktop -> Settings -> Export Telegram Data -> Machine-readable JSON.

─────────────────────────────────────────────────────────────────────────────
TIME-ZERO REPLAY ALGORITHM
─────────────────────────────────────────────────────────────────────────────
The algorithm reproduces the relative timing of the original conversation.

1. Parse all messages and sort them chronologically.
2. The earliest message becomes "time zero" (delay = 0 s).
3. Every subsequent message gets delay = (its_timestamp - time_zero) seconds.
4. At service startup we record the wall-clock start time.
5. Before yielding each message we sleep until (start + delay) is reached.
"""
from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncIterator
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from ..models import TelegramMessage, TelegramGroup

logger = logging.getLogger(__name__)


# TODO: telegram sends the "date" field as a local datetime of the user. 
# This function assumes it's in UTC.
def _parse_date(date_str: str) -> datetime:
    """
    Parses an ISO 8601 string to a timezone-aware datetime.
    """
    dt = datetime.fromisoformat(date_str)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def _extract_text(text_field: Any) -> str:
    """
    Pure function: flatten the Telegram text field to a plain string.

    The field can be:
      • a plain str -> return as-is
      • a list of mixed str / entity-dicts -> concatenate the .text values
    """
    if isinstance(text_field, str):
        return text_field
    if isinstance(text_field, list):
        return "".join(
            part if isinstance(part, str) else part.get("text", "")
            for part in text_field
        )
    return ""


def parse_messages_from_export(data: dict[str, Any]) -> tuple[TelegramMessage, ...]:
    """
    Convert a Telegram Desktop export dict to an immutable
    tuple of TelegramMessage objects.

    Service messages (type != "message") and entries without a date are
    filtered out.
    """
    group_id = int(data["id"])

    return tuple(
        TelegramMessage(
            message_id=msg["id"],
            group = TelegramGroup(telegram_id=group_id),
            text=_extract_text(msg.get("text", "")),
            timestamp=_parse_date(msg["date"]),
            sender_id=str(msg.get("from_id", "")) or None,
            sender_name=msg.get("from"),
            raw=msg,
        )
        for msg in data.get("messages", [])
        if msg.get("type") == "message" and "date" in msg
    )


def sort_by_timestamp(
    messages: tuple[TelegramMessage, ...],
) -> tuple[TelegramMessage, ...]:
    """Return messages in chronological order."""
    return tuple(sorted(messages, key=lambda m: m.timestamp))


def compute_replay_schedule(
    messages: tuple[TelegramMessage, ...],
) -> tuple[tuple[float, TelegramMessage], ...]:
    """
    Pair each message with its replay delay in seconds.

    The earliest message has delay=0 (it is the time-zero reference).
    All others have delay = (timestamp - time_zero).total_seconds().
    """
    if not messages:
        return ()

    sorted_msgs = sort_by_timestamp(messages)
    time_zero = sorted_msgs[0].timestamp

    return tuple(
        ((msg.timestamp - time_zero).total_seconds(), msg)
        for msg in sorted_msgs
    )


def load_export_file(path: str) -> dict[str, Any]:
    """IO function: read and JSON-parse a Telegram Desktop export file."""
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(
            f"Test messages file not found: {path}\n"
            "Export a chat via Telegram Desktop -> Settings -> Export Telegram Data"
            " (Machine-readable JSON) and point TEST_MESSAGES_FILE at it."
        )
    return json.loads(p.read_text(encoding="utf-8"))


async def message_stream_from_file(
    file_path: str,
) -> AsyncIterator[TelegramMessage]:
    """
    Async generator: yield messages from a Telegram Desktop export file,
    replaying them with relative timing.

    """
    logger.info(f"[TEST MODE] Loading messages from: {file_path}")

    raw_data = load_export_file(file_path)
    messages = parse_messages_from_export(raw_data)
    schedule = compute_replay_schedule(messages)

    if not schedule:
        logger.warning("[TEST MODE] No messages found in export file — nothing to replay")
        return

    first_delay, first_msg = schedule[0]
    last_delay, _ = schedule[-1]
    logger.info(
        f"[TEST MODE] Loaded {len(schedule)} messages.  "
        f"Replay spans {last_delay:.1f}s from time-zero "
        f"(first: {first_msg.timestamp.isoformat()})"
    )

    # Record the event-loop time at the start of the replay
    loop_start: float = asyncio.get_event_loop().time()

    for delay_seconds, message in schedule:
        # How many seconds from now until this message is "due"
        elapsed = asyncio.get_event_loop().time() - loop_start
        wait_for = delay_seconds - elapsed

        if wait_for > 0:
            logger.debug(
                f"[TEST MODE] ⏱  sleeping {wait_for:.2f}s before "
                f"msg_id={message.message_id}"
            )
            await asyncio.sleep(wait_for)

        logger.info(
            f"[TEST MODE] ▶  emitting msg_id={message.message_id} "
            f"group={message.group.telegram_id} "
            f"t+{delay_seconds:.1f}s"
        )
        yield message
