
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