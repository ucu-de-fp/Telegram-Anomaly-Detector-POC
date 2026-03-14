"""
Message filtering — pure functions.

─────────────────────────────────────────────────────────────────────────────
FUNCTIONAL PROGRAMMING NOTE
─────────────────────────────────────────────────────────────────────────────
This module contains only *predicates* and *filter pipelines*.

A predicate is a function A → bool.  We compose predicates with standard
higher-order functions (filter, map, all, any) rather than imperative loops.

should_publish is the single filtering predicate for the hot path.
filter_messages shows how a batch of messages can be declaratively filtered
using Python's built-in filter() (a higher-order function).
─────────────────────────────────────────────────────────────────────────────
"""
from __future__ import annotations

import logging

from .models import CacheState, TelegramMessage

logger = logging.getLogger(__name__)

def should_publish(message: TelegramMessage, cache: CacheState) -> bool:
    """
    Pure predicate: should this message be forwarded to RabbitMQ?

    Returns True iff the message's source group is in the pre-computed set
    of groups whose polygon overlaps at least one active zone of interest.

    Complexity: O(1) — the set lookup is constant time regardless of how
    many groups or zones exist.
    """
    return message.group.telegram_id in cache.publishable_group_ids


def filter_messages(
    messages: tuple[TelegramMessage, ...],
    cache: CacheState,
) -> tuple[TelegramMessage, ...]:
    """
    Pure function: filter a batch of messages against the current cache.

    ─────────────────────────────────────────────────────────────────────────
    FUNCTIONAL PROGRAMMING NOTE
    ─────────────────────────────────────────────────────────────────────────
    filter() is a standard higher-order function.  We pass it a *partially
    applied* predicate (using a lambda) so the cache is "baked in" without
    the caller needing to thread it through manually.

    functools.partial would be equally idiomatic:
        from functools import partial
        predicate = partial(should_publish, cache=cache)
        return tuple(filter(predicate, messages))
    ─────────────────────────────────────────────────────────────────────────
    """
    return tuple(filter(lambda m: should_publish(m, cache), messages))
