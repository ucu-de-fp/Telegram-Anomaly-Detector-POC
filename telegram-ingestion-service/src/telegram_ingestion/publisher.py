"""
RabbitMQ publisher — IO layer.

─────────────────────────────────────────────────────────────────────────────
FUNCTIONAL PROGRAMMING NOTE — separating serialisation from IO
─────────────────────────────────────────────────────────────────────────────
serialise_message() is a *pure* function: bytes in, bytes out, no IO.
publish_message() is an *IO* function: it calls the network.

This separation lets us unit-test the serialisation format without spinning
up a real RabbitMQ broker.
─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

import asyncio
import json
import logging

import aio_pika
import aio_pika.abc

from .config import Settings
from .models import TelegramMessage

logger = logging.getLogger(__name__)


# ── Pure serialisation ────────────────────────────────────────────────────────

def serialise_message(message: TelegramMessage) -> bytes:
    """
    Pure function: convert a TelegramMessage to UTF-8 JSON bytes.

    Only the fields useful for downstream consumers are included.
    The `raw` dict is excluded to keep payloads small; add it back here
    if your consumers need the full Telegram payload.
    """
    # payload: dict = {
    #     "message_id": message.message_id,
    #     "group_telegram_id": message.group_telegram_id,
    #     "text": message.text,
    #     "timestamp": message.timestamp.isoformat(),
    #     "sender_id": message.sender_id,
    #     "sender_name": message.sender_name,
    # }
    payload: dict = {
        "groupId": message.group_telegram_id,
        "content": message.text,
        "timestamp": message.timestamp.replace(tzinfo=None).isoformat()
    }
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")


# ── IO: connect ───────────────────────────────────────────────────────────────

async def create_amqp_connection(
    settings: Settings,
) -> aio_pika.abc.AbstractRobustConnection:
    """
    Establish a *robust* AMQP connection.

    aio-pika's RobustConnection automatically reconnects on network failures,
    which is essential for long-running services.
    """
    logger.info(
        f"Connecting to RabbitMQ at {settings.rabbitmq_host}:{settings.rabbitmq_port}"
    )
    while True:
        try:
            return await aio_pika.connect_robust(settings.amqp_url)
        except Exception as e:
            logger.warning("RabbitMQ not ready yet: %s", e)
            await asyncio.sleep(5)


async def create_exchange(
    connection: aio_pika.abc.AbstractRobustConnection,
    exchange_name: str,
) -> tuple[aio_pika.abc.AbstractChannel, aio_pika.abc.AbstractExchange]:
    """
    Open a channel and declare a durable topic exchange.

    Using a *topic* exchange lets downstream consumers filter by routing key
    patterns (e.g. "telegram.message.*").
    """
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=10)

    exchange = await channel.declare_exchange(
        exchange_name,
        aio_pika.ExchangeType.TOPIC,
        durable=True,
    )
    logger.info(f"Declared exchange '{exchange_name}' (topic, durable)")
    return channel, exchange


# ── IO: publish ───────────────────────────────────────────────────────────────

async def publish_message(
    exchange: aio_pika.abc.AbstractExchange,
    routing_key: str,
    message: TelegramMessage,
) -> None:
    """
    Publish one TelegramMessage to the exchange.

    Messages are marked PERSISTENT so they survive broker restarts.
    """
    body = serialise_message(message)

    # java видавала такий формат (точно?)
    # "2026-03-07T00:09:34.183244730"
    # python видає такий
    # 2026-03-06T22:18:01+00:00

    logger.info(f"Publishing to routing key: {routing_key}. Message: {message}. Body: {body}")
    amqp_msg = aio_pika.Message(
        body=body,
        content_type="application/json",
        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        message_id=str(message.message_id),
    )
    await exchange.publish(amqp_msg, routing_key=routing_key)
    logger.info(
        f"→ RabbitMQ [{routing_key}] "
        f"msg_id={message.message_id} group={message.group_telegram_id}"
    )
