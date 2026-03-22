from __future__ import annotations

import asyncio
import json
import logging

import aio_pika
import aio_pika.abc

from .config import Settings
from .models import DetectionMessage, TelegramMessage

logger = logging.getLogger(__name__)


def serialise_message(message: DetectionMessage) -> bytes:
    return message.model_dump_json(by_alias=True).encode("utf-8")


async def create_amqp_connection(
    settings: Settings,
) -> aio_pika.abc.AbstractRobustConnection:
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


async def publish_message(
    exchange: aio_pika.abc.AbstractExchange,
    routing_key: str,
    message: DetectionMessage,
) -> None:
    """
    Publish DetectionMessage to the exchange.
    """
    body = serialise_message(message)

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
        f"msg={message}"
        # f"msg_id={message.message_id} group={message.group_id}"
    )
