"""
Application entry point.

─────────────────────────────────────────────────────────────────────────────
ARCHITECTURE OVERVIEW
─────────────────────────────────────────────────────────────────────────────

  ┌─────────────────────────────────────────────────────────────────────┐
  │                          main.py                                    │
  │                                                                     │
  │  Config ──► DB Pool ──► Cache ──► HTTP Server  (FastAPI/uvicorn)    │
  │                              │                                      │
  │                              └──► Message Pipeline                  │
  │                                      │                              │
  │                              Source (choose one)                    │
  │                                ├── file_source   (PROFILES=test)    │
  │                                └── telegram_source (live)           │
  │                                      │                              │
  │                              filter.should_publish()  [PURE]        │
  │                                      │                              │
  │                              publisher.publish_message() [IO]       │
  └─────────────────────────────────────────────────────────────────────┘

─────────────────────────────────────────────────────────────────────────────
FUNCTIONAL PROGRAMMING NOTE — thin orchestration layer
─────────────────────────────────────────────────────────────────────────────
This module is deliberately thin.  It:
  1. Reads configuration (pure).
  2. Establishes connections (IO).
  3. Loads the initial cache (IO → pure CacheState).
  4. Runs two concurrent async tasks: pipeline + HTTP server.

All business logic lives in the pure modules (geometry, filter, models).
All IO is isolated in database, publisher, and the source sub-modules.
─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

import asyncio
import logging
import os

from collections.abc import AsyncIterator

import uvicorn

from .source.source import get_message_source
from .models import CacheState, TelegramMessage, DetectionMessage
from .error import GroupMappingNotFoundError
from .cache import refresh_cache, get_cache
from .config import get_settings
from .database import create_db_pool
from .filter import should_publish
from .http_api import app as http_app
from .publisher import create_amqp_connection, create_exchange, publish_message

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
)
logger = logging.getLogger(__name__)


# ── Pipeline ──────────────────────────────────────────────────────────────────

from typing import Union

def convert_to_detection_message(
    message: TelegramMessage,
    cache: CacheState,
) -> Union[DetectionMessage, GroupMappingNotFoundError]:
    group_id = cache.group_id_mapping.get(message.telegram_group_id)

    if group_id is None:
        return GroupMappingNotFoundError(message.telegram_group_id)

    return DetectionMessage(
        message_id=message.message_id,
        group_id=group_id,
        content=message.text,
        timestamp=message.timestamp,
        sender_id=message.sender_id,
        sender_name=message.sender_name,
        raw=message.raw,
    )

async def run_message_pipeline(settings, exchange) -> None:
    """
    Core message pipeline: source → filter → publish.
    """
    source: AsyncIterator[TelegramMessage] = get_message_source(settings)

    published = 0
    filtered = 0

    async for message in source:
        logger.info(f'Processing message {message}')
        cache = get_cache()
        if cache is None:
            logger.warning("Cache not loaded yet — skipping message")
            continue

        result: DetectionMessage = convert_to_detection_message(message, cache)
        if isinstance(result, GroupMappingNotFoundError):
            logger.warning(f"Skipping: {result}")
            continue

        if should_publish(message.telegram_group_id, cache):
            logger.info(f"Detection message: {result}")
            await publish_message(exchange, settings.rabbitmq_routing_key, result)
            published += 1
            logger.info(
                f"✓ PUBLISHED  msg_id={message.message_id} "
                f"group={message.telegram_group_id} "
                f'text="{message.text[:60]}"'
            )
        else:
            filtered += 1
            logger.info(
                f"✗ filtered   msg_id={message.message_id} "
                f"group={message.telegram_group_id} (not in any active zone)"
            )

    # Reached only in test mode (file source exhausted)
    logger.info(
        f"Pipeline finished.  published={published}, filtered_out={filtered}"
    )


# ── HTTP server ───────────────────────────────────────────────────────────────

async def run_http_server(settings) -> None:
    """Run the FastAPI cache-management API on a configurable port."""
    config = uvicorn.Config(
        app=http_app,
        host=settings.http_host,
        port=settings.http_port,
        log_level="warning",        # uvicorn access logs would be noisy
        loop="none",                # reuse the existing asyncio loop
    )
    server = uvicorn.Server(config)
    logger.info(
        f"HTTP API listening on {settings.http_host}:{settings.http_port}"
    )
    await server.serve()


# ── Entry point ───────────────────────────────────────────────────────────────

async def main() -> None:
    settings = get_settings()

    logger.info(
        "═══════════════════════════════════════════════════════════\n"
        "  telegram-ingestion-service  starting up\n"
        f"  message source: {settings.message_source}\n"
        "═══════════════════════════════════════════════════════════"
    )

    logger.info(f'Starting with settings: {settings}')

    # ── Connect to infrastructure ─────────────────────────────────────────────
    db_pool = await create_db_pool(settings)
    amqp_conn = await create_amqp_connection(settings)
    # TODO: Rework for non-default exchange
    # _, exchange = await create_exchange(amqp_conn, settings.rabbitmq_exchange)
    channel = await amqp_conn.channel()
    exchange = channel.default_exchange
    queue = await channel.declare_queue(
        settings.rabbitmq_routing_key,
        durable=True
    )

    # ── Load initial cache from DB ────────────────────────────────────────────
    logger.info("Loading initial cache from database …")
    await refresh_cache(db_pool)

    # ── Inject DB pool into HTTP app state ────────────────────────────────────
    http_app.state.db_pool = db_pool

    # ── Run pipeline + HTTP server concurrently ───────────────────────────────
    await asyncio.gather(
        run_message_pipeline(settings, exchange),
        run_http_server(settings),
        return_exceptions=False,
    )


def entrypoint() -> None:
    """Synchronous wrapper called by the 'start' Poetry script."""
    asyncio.run(main())


if __name__ == "__main__":
    entrypoint()
