# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — builder: install all dependencies into an in-project venv
# ─────────────────────────────────────────────────────────────────────────────
FROM python:3.12-slim AS builder

ENV POETRY_VERSION=1.8.3 \
    POETRY_HOME=/opt/poetry \
    POETRY_VIRTUALENVS_IN_PROJECT=true \
    POETRY_NO_INTERACTION=1 \
    PIP_NO_CACHE_DIR=1

RUN pip install "poetry==${POETRY_VERSION}"
ENV PATH="${POETRY_HOME}/bin:${PATH}"

WORKDIR /app

# Copy manifests first — this layer is cached unless deps change
COPY pyproject.toml poetry.lock* README.md ./
COPY src/ src/

# Install only third-party runtime deps (skip the app package itself)
# RUN poetry install --only main --no-root
RUN poetry install --only main



# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    # Add src/ to the path so 'telegram_ingestion' is importable without
    # a package install step — avoids all editable-install / wheel issues.
    # PYTHONPATH=/app/src \
    PATH="/app/.venv/bin:${PATH}"

RUN useradd --system --create-home --home-dir /app appuser

WORKDIR /app

# Copy the pre-resolved venv (all third-party deps)
COPY --from=builder /app/.venv /app/.venv
# Copy the application source
COPY --from=builder /app/src /app/src

RUN mkdir -p /app/session /app/test_data \
    && chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

CMD ["python", "-m", "telegram_ingestion.main"]