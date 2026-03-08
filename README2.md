
## Запуск виключно інфраструктурних сервісів в докері
```bash
docker compose --profile infra up -d
```


## Локальний запуск з терміналу
```bash
cd telegram-ingestion-service

set -a
source .env.dev.local
set +a

PYTHONPATH=src poetry run python -m telegram_ingestion.main
```

## Запуск всього
```bash
APP_ENV=dev docker compose --profile infra --profile app --parallel 8 up --build
```