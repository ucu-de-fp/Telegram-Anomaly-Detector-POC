"""
Live Telegram message source via Telethon.

─────────────────────────────────────────────────────────────────────────────
HOW TELETHON AUTHENTICATION WORKS
─────────────────────────────────────────────────────────────────────────────
Telethon authenticates via MTProto using:
  • TELEGRAM_API_ID   — obtain at https://my.telegram.org/apps
  • TELEGRAM_API_HASH — same page

On first run the client will prompt for a phone number and a one-time code
sent by Telegram.  The session is persisted to a file (TELEGRAM_SESSION_NAME).
For Docker deployments, mount the session file as a volume so it survives
container restarts.

─────────────────────────────────────────────────────────────────────────────
CHAT-ID NORMALISATION
─────────────────────────────────────────────────────────────────────────────
Telegram's internal IDs for groups / supergroups / channels are negative
integers in Telethon events.  The group-management-service may store them
in various formats (with or without leading '-').  We normalise both sides
to their absolute-value string for matching.

─────────────────────────────────────────────────────────────────────────────
FUNCTIONAL PROGRAMMING NOTE — push → pull conversion
─────────────────────────────────────────────────────────────────────────────
Telethon uses a *callback/event* model (push-based):
  client.on(events.NewMessage()) → async def handler(event): ...

Our pipeline uses an *async generator* model (pull-based):
  async for message in source: ...

We bridge the two with an asyncio.Queue.  This is the standard functional
pattern for converting push-based event streams into pull-based ones — the
Queue acts as a bounded channel between producer and consumer.
─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncIterator

from telethon import TelegramClient, events
from telethon.tl.types import Message as TLMessage

from ..config import Settings
from ..models import TelegramMessage, TelegramGroup

logger = logging.getLogger(__name__)


# ── Pure conversion ───────────────────────────────────────────────────────────

def _normalise_chat_id(chat_id: int | str) -> str:
    """
    Pure helper: strip leading '-' so we can compare absolute IDs.

    Both the event chat_id and the stored telegram_group_id are normalised
    before comparison, making the matching format-agnostic.
    """
    return str(chat_id).lstrip("-100")


def _telethon_event_to_domain(
    event: events.NewMessage.Event,
    matched_group_id: str,
) -> TelegramMessage:
    """
    Pure(-ish) conversion: Telethon event → immutable TelegramMessage.

    We read from the event object only — no further IO.
    """
    msg: TLMessage = event.message
    return TelegramMessage(
        message_id=msg.id,
        group=TelegramGroup(telegram_id=matched_group_id),
        text=msg.text or "",
        timestamp=msg.date,
        sender_id=str(msg.sender_id) if msg.sender_id else None,
        sender_name=None,
        raw=msg.to_dict(),
    )


# ── IO: async generator ───────────────────────────────────────────────────────

async def message_stream_from_telegram(
    settings: Settings,
    group_ids: frozenset[str],
) -> AsyncIterator[TelegramMessage]:
    """
    Async generator: connect to Telegram and yield messages from watched groups.

    Only messages whose chat_id (normalised) matches one of the supplied
    group_ids are enqueued.  Pass the full set of known group IDs from the
    cache so that the filter is applied as early as possible.

    If a cache refresh adds new groups mid-run, restart the generator (or
    call this function again) to pick them up — or simply let the existing
    generator catch all messages and rely on should_publish() in the pipeline.
    """
    if not settings.telegram_api_id or not settings.telegram_api_hash:
        raise ValueError(
            "TELEGRAM_API_ID and TELEGRAM_API_HASH must be set in non-test mode"
        )

    # Precompute a set of normalised IDs for O(1) lookup per event
    normalised_ids: frozenset[str] = frozenset(
        _normalise_chat_id(gid) for gid in group_ids
    )
    # Keep original IDs indexed by normalised form for clean domain objects
    id_map: dict[str, str] = {
        _normalise_chat_id(gid): gid for gid in group_ids
    }

    # Queue bridges the push (callback) and pull (generator) worlds
    queue: asyncio.Queue[TelegramMessage] = asyncio.Queue(maxsize=500)

    client = TelegramClient(
        settings.telegram_session_name,
        settings.telegram_api_id,
        settings.telegram_api_hash,
    )

    @client.on(events.NewMessage())
    async def _on_new_message(event: events.NewMessage.Event) -> None:
        logger.info(f'Received telegram event: {event}')
        norm = _normalise_chat_id(event.chat_id)
        logger.info(f'Normalized ids: {normalised_ids}, chat id: {event.chat_id}, normzlized id: {norm}')
        if norm not in normalised_ids:
            return                          # not a watched group — skip fast

        logger.info("processing message")

        matched_id = id_map[norm]
        msg = _telethon_event_to_domain(event, matched_id)

        logger.info(f"putting message {msg} for matched id {matched_id}")

        try:
            queue.put_nowait(msg)
        except asyncio.QueueFull:
            logger.warning(
                f"Message queue full — dropping msg_id={msg.message_id}. "
                "Consider increasing maxsize or speeding up the consumer."
            )

    await client.start()
    logger.info(
        f"[LIVE MODE] Telethon connected.  "
        f"Watching {len(group_ids)} group(s): {sorted(group_ids)}"
    )

    try:
        while client.is_connected():
            try:
                # Poll with a short timeout so we can detect disconnections
                message = await asyncio.wait_for(queue.get(), timeout=2.0)
                yield message
            except asyncio.TimeoutError:
                continue
    finally:
        logger.info("[LIVE MODE] Disconnecting Telethon client …")
        await client.disconnect()
