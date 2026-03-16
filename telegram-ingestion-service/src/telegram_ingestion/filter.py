from __future__ import annotations

import logging

from .models import CacheState, TelegramMessage

logger = logging.getLogger(__name__)

def should_publish(message: TelegramMessage, cache: CacheState) -> bool:
    """
    Returns True if the message's source group is in the pre-computed set
    of groups that should be processed.
    """
    return message.group.telegram_id in cache.publishable_group_ids


def filter_messages(
    messages: tuple[TelegramMessage, ...],
    cache: CacheState,
) -> tuple[TelegramMessage, ...]:
    return tuple(filter(lambda m: should_publish(m, cache), messages))
