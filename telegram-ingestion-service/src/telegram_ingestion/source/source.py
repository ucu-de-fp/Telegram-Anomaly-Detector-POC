from collections.abc import AsyncIterator
from ..models import TelegramMessage
from ..cache import get_cache
import logging

logger = logging.getLogger(__name__)


def get_message_source(settings) -> AsyncIterator[TelegramMessage]:
    if settings.message_source == "FILE":
        from .file_source import message_stream_from_file
        source = message_stream_from_file(settings.test_messages_file)
        logger.info("[TEST MODE] Using file-based message source")
    elif settings.message_source == "TELEGRAM":
        from .telegram_source import message_stream_from_telegram
        current_cache = get_cache()
        group_ids: frozenset[str] = (
            frozenset(g.telegram_id for g in current_cache.groups)
            if current_cache else frozenset()
        )
        logger.info(f'Listening to groups with ids: {group_ids}')
        source = message_stream_from_telegram(settings, group_ids)
        logger.info("[LIVE MODE] Using Telethon message source")
    else:
        from .random_messages_source import message_stream_random
        source = message_stream_random(settings)
        logger.info("[TEST MODE] Using randomized message source")
    return source