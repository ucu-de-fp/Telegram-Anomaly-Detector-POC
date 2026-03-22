"""
Random-message test source — used when MESSAGE_SOURCE is set to "RANDOM_MESSAGES".

─────────────────────────────────────────────────────────────────────────────
SOURCE FORMAT  (random_messages.toml)
─────────────────────────────────────────────────────────────────────────────
A TOML file with three arrays of tables:

  [[groups]]          – Telegram groups to sample from
  id   = 1001         # required  (integer, used as group_telegram_id)
  name = "Dev Chat"   # optional  (informational only)

  [[senders]]         – Users who "send" the random messages
  id   = "user42"     # required  (string, used as sender_id)
  name = "Alice"      # optional  (used as sender_name)

  [[messages]]        # Pool of message bodies to draw from
  text = "Hello!"     # required  (plain string — safe to embed JSON/quotes)

─────────────────────────────────────────────────────────────────────────────
CONFIGURATION (environment variables)
─────────────────────────────────────────────────────────────────────────────
  RANDOM_SOURCE_CONFIG   path to the TOML file

  RANDOM_SOURCE_INTERVAL_SECONDS    mean seconds between emitted messages (float > 0)

  RANDOM_SOURCE_JITTER   fraction of interval to add random ±jitter (0–1)
"""
from __future__ import annotations

import asyncio
import logging
import os
import random
from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from ..config import Settings

try:
    import tomllib  # Python 3.11+
except ModuleNotFoundError:
    import tomli as tomllib  # pip install tomli for 3.10 and below  # type: ignore[no-redef]

from ..models import TelegramMessage, TelegramGroup

logger = logging.getLogger(__name__)

# ── Default config path (sits next to this file) ─────────────────────────────

_DEFAULT_CONFIG = Path(__file__).parent / "random_messages.toml"


# ── Pure data types ───────────────────────────────────────────────────────────

@dataclass(frozen=True)
class _Sender:
    id: str
    name: str | None


@dataclass(frozen=True)
class _Group:
    id: int
    name: str | None


@dataclass(frozen=True)
class RandomSourceConfig:
    """Immutable, validated configuration parsed from the TOML file."""
    groups: tuple[_Group, ...]
    senders: tuple[_Sender, ...]
    messages: tuple[str, ...]          # plain text bodies
    interval: float                    # mean seconds between messages
    jitter: float                      # fraction of interval for ± jitter


# ── Pure parsing helpers ──────────────────────────────────────────────────────

def _parse_groups(raw: list[dict]) -> tuple[_Group, ...]:
    return tuple(
        _Group(id=int(g["id"]), name=g.get("name"))
        for g in raw
    )


def _parse_senders(raw: list[dict]) -> tuple[_Sender, ...]:
    return tuple(
        _Sender(id=str(s["id"]), name=s.get("name"))
        for s in raw
    )


def _parse_messages(raw: list[dict]) -> tuple[str, ...]:
    return tuple(m["text"] for m in raw)


def _read_env_float(name: str, default: float) -> float:
    """Pure-ish helper: read a float from env, fall back to default."""
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        return float(raw)
    except ValueError:
        logger.warning(
            "Invalid value for %s=%r — using default %.2f", name, raw, default
        )
        return default


# ── IO: load & validate config ────────────────────────────────────────────────

def load_random_config(path: str | Path | None = None) -> RandomSourceConfig:
    """
    IO function: read the TOML config and return a validated RandomSourceConfig.

    Resolution order for the file path:
      1. `path` argument (if given)
      2. RANDOM_SOURCE_CONFIG env variable
      3. random_messages.toml next to this module
    """
    resolved = (
        Path(path)
        if path is not None
        else Path(os.environ.get("RANDOM_SOURCE_CONFIG", str(_DEFAULT_CONFIG)))
    )

    if not resolved.exists():
        raise FileNotFoundError(
            f"Random source config not found: {resolved}\n"
            "Create a TOML file with [[groups]], [[senders]], and [[messages]] tables\n"
            "or point RANDOM_SOURCE_CONFIG at an existing one."
        )

    with resolved.open("rb") as fh:
        data = tomllib.load(fh)

    groups  = _parse_groups(data.get("groups", []))
    senders = _parse_senders(data.get("senders", []))
    messages = _parse_messages(data.get("messages", []))

    if not groups:
        raise ValueError(f"random_source: no [[groups]] defined in {resolved}")
    if not senders:
        raise ValueError(f"random_source: no [[senders]] defined in {resolved}")
    if not messages:
        raise ValueError(f"random_source: no [[messages]] defined in {resolved}")

    interval = _read_env_float("RANDOM_SOURCE_INTERVAL_SECONDS", 5.0)
    jitter   = _read_env_float("RANDOM_SOURCE_JITTER", 0.2)

    if interval <= 0:
        raise ValueError(f"RANDOM_SOURCE_INTERVAL_SECONDS must be > 0, got {interval}")
    if not 0.0 <= jitter <= 1.0:
        raise ValueError(f"RANDOM_SOURCE_JITTER must be between 0 and 1, got {jitter}")

    return RandomSourceConfig(
        groups=groups,
        senders=senders,
        messages=messages,
        interval=interval,
        jitter=jitter,
    )


# ── Pure: build a single random TelegramMessage ───────────────────────────────

def _make_random_message(
    cfg: RandomSourceConfig,
    message_id: int,
    rng: random.Random,
) -> TelegramMessage:
    """
    Pure function (given a seeded RNG): sample group, sender, and text
    and return a fresh TelegramMessage stamped with the current UTC time.
    """
    group  = rng.choice(cfg.groups)
    sender = rng.choice(cfg.senders)
    text   = rng.choice(cfg.messages)

    return TelegramMessage(
        message_id=message_id,
        telegram_group_id=group.id,
        text=text,
        timestamp=datetime.now(tz=timezone.utc),
        sender_id=sender.id,
        sender_name=sender.name,
        raw={},
    )


# ── Pure: compute next delay with jitter ──────────────────────────────────────

def _next_delay(interval: float, jitter: float, rng: random.Random) -> float:
    """
    Pure function: return a delay sampled uniformly in
    [interval*(1-jitter), interval*(1+jitter)].
    """
    lo = interval * (1.0 - jitter)
    hi = interval * (1.0 + jitter)
    return rng.uniform(lo, hi)


# ── IO: async generator ───────────────────────────────────────────────────────

async def message_stream_random(
    settings: Settings,
    *,
    seed: int | None = None,
) -> AsyncIterator[TelegramMessage]:
    """
    Async generator: yield randomly-composed TelegramMessages indefinitely.

    Parameters
    ----------
    config_path:
        Path to the TOML config file. Falls back to RANDOM_SOURCE_CONFIG
        env var, then to random_messages.toml next to this module.
    seed:
        Optional RNG seed for reproducible test runs.
    """
    cfg = load_random_config(settings.random_source_config)
    rng = random.Random(seed)

    logger.info(
        "[RANDOM MODE] Loaded config — %d group(s), %d sender(s), %d message(s).  "
        "Interval: %.2fs ± %.0f%%",
        len(cfg.groups),
        len(cfg.senders),
        len(cfg.messages),
        settings.random_source_interval_seconds,
        settings.random_source_jitter * 100,
    )

    message_id = 1

    while True:
        delay = _next_delay(settings.random_source_interval_seconds, settings.random_source_jitter, rng)
        logger.debug("[RANDOM MODE] ⏱  next message in %.2fs", delay)
        await asyncio.sleep(delay)

        message = _make_random_message(cfg, message_id, rng)
        message_id += 1

        logger.info(
            "[RANDOM MODE] ▶  emitting msg_id=%d  group=%d  sender=%s",
            message.message_id,
            message.telegram_group_id,
            message.sender_name or message.sender_id,
        )
        yield message