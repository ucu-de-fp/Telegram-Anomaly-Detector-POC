# telegram-ingestion-service

A Telegram message ingestion service built with **Telethon** and written in a
**functional programming style**.  This is a course project demonstrating
functional principles in Python: immutable data, pure functions, IO isolation,
higher-order functions, and function composition.

---

## Table of contents

1. [How Poetry works — install once, use everywhere](#poetry-setup)
2. [Project setup](#project-setup)
3. [Running locally](#running-locally)
4. [Test mode (file replay)](#test-mode)
5. [Live mode (Telegram)](#live-mode)
6. [Docker / Docker Compose](#docker)
7. [HTTP cache API](#http-cache-api)
8. [Architecture & functional design notes](#architecture)
9. [Database schema assumptions](#database-schema)

---

## Poetry setup

**Do I install Poetry globally and then use it to create a virtual env?**

Yes — exactly.  Poetry is a *tool*, not a library, so it lives outside any
project's virtual environment.  Here is the recommended flow:

```bash
# 1. Install Poetry globally (into its own isolated Python install)
#    The official installer never modifies system Python or pip.
curl -sSL https://install.python-poetry.org | python3 -

# 2. Add Poetry to your PATH (add this line to ~/.bashrc or ~/.zshrc)
export PATH="$HOME/.local/bin:$PATH"

# 3. Verify
poetry --version      # e.g. Poetry (version 1.8.3)
```

When you run `poetry install` inside a project, Poetry automatically:
- Creates a virtual environment (by default in `~/.cache/pypoetry/virtualenvs/`)
- Installs all dependencies declared in `pyproject.toml` into that venv
- Generates / updates `poetry.lock` for reproducible installs

You **never** need to run `python -m venv` or `pip install` manually.

**Useful commands:**

| Command | What it does |
|---|---|
| `poetry install` | Install all deps (including dev) from `pyproject.toml` |
| `poetry install --only main` | Install only runtime deps (no dev tools) |
| `poetry add <pkg>` | Add a new dependency |
| `poetry add --group dev <pkg>` | Add a dev-only dependency |
| `poetry run <cmd>` | Run a command inside the managed venv |
| `poetry shell` | Activate the venv in your current shell |
| `poetry env info` | Show which venv Poetry is using |
| `poetry lock` | Regenerate `poetry.lock` without installing |

---

## Project setup

```bash
# Clone / navigate to the project
cd telegram-ingestion-service

# Install dependencies (creates venv automatically)
poetry install

# Copy the example env file and fill in your values
cp .env.example .env
```

Required Python version: **≥ 3.12** (3.14 recommended; set in `pyproject.toml`).

If you need Python 3.14 specifically and it is not your system default, use
[pyenv](https://github.com/pyenv/pyenv):

```bash
pyenv install 3.14.0
pyenv local 3.14.0    # writes .python-version
poetry install        # Poetry will pick up the pyenv version automatically
```

---

## Running locally

```bash
# Test mode (reads from a local JSON file — no Telegram account needed)
PROFILES=test poetry run start

# Or activate the venv and run directly
poetry shell
PROFILES=test python -m telegram_ingestion.main
```

---

## Test mode

Set `PROFILES=test` (or any value containing the word `test`, e.g. `dev,test`).

The service reads messages from a **Telegram Desktop JSON export** file:

1. In Telegram Desktop: **Settings → Export Telegram Data → Machine-readable JSON**.
2. Copy the resulting `result.json` to `test_data/result.json` (or configure
   `TEST_MESSAGES_FILE` to point anywhere).

**Time-zero replay algorithm:**
- The service finds the earliest message in the file → that is `t = 0`.
- Every subsequent message is scheduled at its original time offset from `t = 0`.
- This faithfully replays the original conversation rhythm, just shifted to
  start at service boot time.

A sample file is provided at `test_data/result_example.json`.

---

## Live mode

Set `PROFILES=prod` (or any value that does **not** contain `test`).

You need a Telegram API key:
1. Go to <https://my.telegram.org/apps> and log in.
2. Create an application — copy the **App api_id** and **App api_hash**.
3. Set `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` in your `.env`.

**First run (interactive authentication):**
```bash
poetry run start
# Telethon will prompt:
#   Please enter your phone (or bot token): +38099...
#   Please enter the code you received: 12345
```

The session is persisted to the file at `TELEGRAM_SESSION_NAME` (default:
`session/telegram_session`).  Subsequent runs are non-interactive.

For Docker: run the container interactively once to generate the session file,
then mount the `session/` directory as a volume.

---

## Docker

Build the image:
```bash
docker build -t telegram-ingestion-service .
```

### Docker Compose

Copy the relevant service block from `docker-compose.example.yml` into your
project's `docker-compose.yml`.

**Live mode:**
```bash
docker compose up telegram-ingestion-service
```

**Test mode:**
```bash
docker compose --profile test up telegram-ingestion-service-test
```

The test-mode container mounts `./test_data` into `/app/test_data` so you can
drop a `result.json` file without rebuilding the image.

**Generate the Telethon session file in Docker (first time only):**
```bash
docker compose run --rm -it telegram-ingestion-service
# Enter phone number and OTP when prompted.
# The session is saved to the 'telegram_session' named volume.
```

---

## HTTP cache API

The service exposes a small REST API for resetting the in-memory cache.
Call these endpoints after modifying groups or zones in the database.

| Endpoint | Method | Description |
|---|---|---|
| `/health` | GET | Liveness probe |
| `/cache/status` | GET | Current cache stats (no DB call) |
| `/cache/reset` | POST | Reload groups + zones, recompute filter |
| `/cache/reset/groups` | POST | Reload only `telegram_groups` |
| `/cache/reset/zones` | POST | Reload only `zone_of_interest` |

Default port: **8080** (configure via `HTTP_PORT`).

Interactive docs: <http://localhost:8080/docs>

Example:
```bash
curl -X POST http://localhost:8080/cache/reset
# {"status":"reset","groups_count":5,"zones_count":3,"publishable_groups_count":2,...}
```

---

## Architecture

### Functional design principles applied

**1. Immutable data** — All domain objects (`TelegramGroup`, `ZoneOfInterest`,
`TelegramMessage`, `CacheState`) are `@dataclass(frozen=True)`.  Once created,
they cannot be changed.

**2. Pure functions** — `geometry.py` and `filter.py` contain only pure
functions: given the same arguments they always return the same result and have
no side effects.  They are trivially testable without any infrastructure.

**3. IO isolation** — Side effects (DB reads, RabbitMQ publish, Telethon
connection) are confined to dedicated IO modules (`database.py`, `publisher.py`,
`source/`).  The pure core never performs IO.

**4. Higher-order functions** — `filter.filter_messages` uses Python's built-in
`filter()`.  `geometry.compute_publishable_group_ids` uses a generator
expression (lazy map + filter).  `geometry.group_intersects_any_active_zone`
uses `any()` with a generator for short-circuit evaluation.

**5. Atomic reference replacement** — The cache is updated by building a new
immutable `CacheState` and swapping the module-level reference under a lock,
rather than mutating fields in place.  Readers always see a consistent snapshot.

**6. Push → Pull stream conversion** — Telethon's callback-based event system
(push) is bridged to an `async for` generator (pull) via an `asyncio.Queue` —
a standard functional pattern for decoupling producers from consumers.

### Module map

```
src/telegram_ingestion/
├── config.py          Pure data — settings loaded from env vars
├── models.py          Immutable domain types (frozen dataclasses)
├── geometry.py        Pure spatial functions (Shapely intersection logic)
├── filter.py          Pure predicates — should_publish, filter_messages
├── database.py        IO — read telegram_groups + zone_of_interest from DB
├── cache.py           Isolated mutable state — atomic CacheState replacement
├── publisher.py       IO — serialise (pure) + publish to RabbitMQ (IO)
├── http_api.py        IO — FastAPI HTTP endpoints for cache management
├── main.py            Orchestration — wires IO layers around pure core
└── source/
    ├── file_source.py     IO+Pure — parse export file (pure) + replay (IO)
    └── telegram_source.py IO — Telethon client, push→pull queue bridge
```

---

## Database schema

The service reads (never writes) two tables created by **group-management-service**:

```sql
-- Telegram groups tracked by the system
CREATE TABLE telegram_groups (
    id                BIGSERIAL PRIMARY KEY,
    telegram_group_id TEXT NOT NULL,          -- raw Telegram chat ID
    polygon           geometry(Polygon, 4326)  -- geographic bounding area
    -- ... other columns ignored by this service
);

-- Operator-defined areas of interest
CREATE TABLE zone_of_interest (
    id      BIGSERIAL PRIMARY KEY,
    polygon geometry(Polygon, 4326) NOT NULL,
    active  BOOLEAN NOT NULL DEFAULT true
    -- ... other columns ignored by this service
);
```

Messages from a group are forwarded to RabbitMQ **only if** the group's
`polygon` intersects at least one `zone_of_interest` where `active = true`.

The intersection check uses Shapely's DE-9IM `intersects` predicate, which
returns `true` for any shared point (including boundary touches).
