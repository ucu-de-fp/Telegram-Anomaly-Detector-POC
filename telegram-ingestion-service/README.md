# telegram-ingestion-service

A Telegram message ingestion service built with **Telethon**.

## Poetry setup

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
cd telegram-ingestion-service

set -a
source .env.dev.local
set +a

PYTHONPATH=src poetry run python -m telegram_ingestion.main
```

---

## Test mode

Set `MESSAGE_SOURCE=FILE`

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

