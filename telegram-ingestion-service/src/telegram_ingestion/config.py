from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    # Values: TELEGRAM, RANDOM_MESSAGES, FILE
    message_source: str = Field(default="RANDOM_MESSAGES", alias="MESSAGE_SOURCE")

    # ── Telegram (only needed when MESSAGE_SOURCE=TELEGRAM) ────────────────────
    telegram_api_id: int = Field(default=0, alias="TELEGRAM_API_ID")
    telegram_api_hash: str = Field(default="", alias="TELEGRAM_API_HASH")
    # Path (without extension) for the Telethon session file
    telegram_session_name: str = Field(
        default="session/telegram_session", alias="TELEGRAM_SESSION_NAME"
    )

    # ── Test-mode source file ─────────────────────────────────────────────────
    # Path to a Telegram Desktop "Export chat history" JSON file.
    test_messages_file: str = Field(
        default="test_data/result.json", alias="TEST_MESSAGES_FILE"
    )

    # ── Test-mode random message source config ─────────────────────────────────────────────────
    random_source_config: str = Field(
        default="", 
        alias="RANDOM_SOURCE_CONFIG"
    )
    random_source_interval_seconds: float = Field(default="5.0", alias="RANDOM_SOURCE_INTERVAL_SECONDS")
    random_source_jitter: float = Field(default="0.2", alias="RANDOM_SOURCE_JITTER")

    # ── PostgreSQL
    db_host: str = Field(default="postgres", alias="DB_HOST")
    db_port: int = Field(default=5432, alias="DB_PORT")
    db_name: str = Field(default="admin", alias="DB_NAME")
    db_user: str = Field(default="monitoring", alias="DB_USER")
    db_password: str = Field(default="monitoring123", alias="DB_PASSWORD")

    # ── RabbitMQ ──────────────────────────────────────────────────────────────
    rabbitmq_host: str = Field(default="rabbitmq", alias="RABBITMQ_HOST")
    rabbitmq_port: int = Field(default=5672, alias="RABBITMQ_PORT")
    rabbitmq_user: str = Field(default="monitoring", alias="RABBITMQ_USERNAME")
    rabbitmq_password: str = Field(default="monitoring123", alias="RABBITMQ_PASSWORD")
    # Topic exchange name; downstream consumers bind queues to this exchange
    rabbitmq_exchange: str = Field(
        default="telegram.messages", alias="RABBITMQ_EXCHANGE"
    )
    rabbitmq_routing_key: str = Field(
        default="telegram.message.ingested", alias="RABBITMQ_ROUTING_KEY"
    )

    # ── HTTP API (cache management endpoints) ─────────────────────────────────
    http_host: str = Field(default="0.0.0.0", alias="HTTP_HOST")
    http_port: int = Field(default=8080, alias="HTTP_PORT")

    # ── Derived properties  ──────────────────────────────────────

    @property
    def db_dsn(self) -> str:
        """asyncpg-compatible connection string."""
        return (
            f"postgresql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )

    @property
    def amqp_url(self) -> str:
        """aio-pika compatible AMQP URL."""
        return (
            f"amqp://{self.rabbitmq_user}:{self.rabbitmq_password}"
            f"@{self.rabbitmq_host}:{self.rabbitmq_port}/"
        )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """
    Return the singleton Settings instance.

    lru_cache ensures the env / .env file is read exactly once.
    """
    return Settings()
