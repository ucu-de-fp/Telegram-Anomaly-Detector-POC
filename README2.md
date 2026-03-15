# Telegram anomaly detector POC

## 1. Запуск
### 1.1 Запуск всього стеку в докері
`.env` файли дозволяють розділити різні оточення. Формат назви .env-файлу для docker - `<service-name>/.env.<APP_ENV>.docker`.  
Таким чином, `APP_ENV=dev` завантажить .env-змінні з файлу `<service-name>/.env.dev.docker`. Наразі, реалізовано тільки для `telegram-ingestion-service`.
#### 1.1.1 Запускаємо команду одноразово, щоб підключити сервіс до телеграму
```bash
docker compose run --rm telegram-ingestion-service
```

#### 1.1.2 Запускаємо стек
```bash
APP_ENV=dev docker compose --profile infra --profile app --parallel 8 up --build
```

### 1.2 Локальний запуск окремих сервісів для розробки

#### 1.2.1 Запускаємо спільні сервіси в докері
```bash
docker compose --profile infra up -d
```
#### 1.2.2 Конфігуруємо .env-файл потрібного сервісу, вказавши адреси спільних сервісів:
Postgres: `localhost:5432`  
RabbitMQ: `localhost:5672`
#### 1.2.3 Запускаємо потрібний сервіс з командної строчки/через IDE (на прикладі telegram ingestion)
```bash
cd telegram-ingestion-service

set -a
source .env.dev.local
set +a

PYTHONPATH=src poetry run python -m telegram_ingestion.main
```

## 2. Робота з додатком
### 2.1 Додаємо в БД телеграм-групи
Переходимо на http://localhost:3000, додаємо групи які нас потенційно цікавлять. Щоб пайплайнт обробляв групу - необхідно також вказати її географічну прив'язку
### 2.2 Обираємо зону інтересу
Зона інтересу - це географічна область, повідомлення з якої нас цікавлять. `telegram-ingestion-service` буде передавати далі в пайплайн тільки повідомлення з груп, які входять в одну з браних зон інтересу
### 2.3 (ТИМЧАСОВО) щоб скинути кеш груп і зон інтересу на telegram-ingestion-service - перезавантажуємо додаток