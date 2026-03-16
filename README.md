# Telegram anomaly detector POC

Функціональний/реактивний дата-пайплайн з детектором аномалій для відслідковування подій в телеграм-групах і їх відображення на веб-дашборді

Складається з 7 компонентів:
- **postgres**: зберігає дані про телеграм-групи і зони інтересу  
- **rabbitMQ**: використовується для обміну даними між сервісами
- **telegram-ingestion-service**: Python-сервіс, читає повідомлення з телеграм-груп, фільтрує, передає в **anomaly-detection-service**
- **anomaly-detection-service**: детектор аномалій (Spring Boot)
- **notification-service**: Spring WebFlux-сервіс для передачі потоку аномалій на фронт-енд через server-sent events
- **admin-api-service**: API для керування групами й зонами інтересу (Spring MVC)
- **frontend**: UI для адміністратора, зроблено на React

Фронт-енд доступний за адресою http://localhost:3000

## 1. Запуск
### 1.1 Конфігурування
Конфігурування додатку виконується за допомогою `.env`-файлів. Оскільки нас цікавить як розгортання в докері, так і запуск окремих сервісів без докеру - використовується неймінг `.env.<APP_ENV>.local` та `.env.<APP_ENV>.docker` (див. приклад в `telegram-ingestion-service`). Наразі, .env-файли реалізовано тільки для `telegram-ingestion-service`.  

За замовчуванням, сервіс буде генерувати мок-дані на основі файлу конфігурації `random_messages.toml`. Для переключення на читання з телеграму, необхідно проставити значення `MESSAGE_SOURCE=TELEGRAM` та заповнити `TELEGRAM_API_ID/TELEGRAM_API_HASH` (отримуються на https://my.telegram.org/apps)

### 1.2 Запуск всього стеку в докері
Необхідно перейменувати `telegram-ingestion-service/.env.dev.docker.example` в `.env.dev.docker`. docker-версія додатку буде працювати без додаткових змін в конфігурації.
#### 1.2.1 (Одноразово) Ініціалізуємо додаток. Ці кроки потрібні тільки у випадку, якщо telegram-ingestion-service читає повідомлення з телеграму, а не з тестових джерел
##### 1.2.1.1 Запускаємо admin-api-service щоб ініціалізувати таблиці telegram_groups і zones_of_interest в БД (без них не запуститься telegram-ingestion-service в наступному кроці). Після старту додатку - зупиняємо виконання
```bash
docker compose --profile infra run --rm admin-api-service
```
##### 1.2.1.2 Запускаємо команду одноразово, щоб підключити сервіс до телеграму. Після вводу номеру телефону і одноразового коду - зупиняємо виконання.
```bash
APP_ENV=dev docker compose --profile infra run --rm telegram-ingestion-service
```

#### 1.2.2 Запускаємо стек
```bash
APP_ENV=dev docker compose --profile infra --profile app --parallel 8 up --build
```

### 1.3 Локальний запуск окремих сервісів для розробки
Необхідно перейменувати `telegram-ingestion-service/.env.dev.local.example` в `.env.dev.local` та, в залежності від обраного джерела даних, сконфігурувати відповідні змінні оточення.
#### 1.3.1 Запускаємо спільні сервіси в докері
```bash
docker compose --profile infra up -d
```
#### 1.3.2 Конфігуруємо .env-файл потрібного сервісу, вказавши адреси спільних сервісів:
Postgres: `localhost:5432`  
RabbitMQ: `localhost:5672`
#### 1.3.3 Запускаємо потрібний сервіс з командної строчки/через IDE (на прикладі telegram ingestion)
```bash
cd telegram-ingestion-service

set -a
source .env.dev.local
set +a

PYTHONPATH=src poetry run python -m telegram_ingestion.main
```

## 2. Робота з додатком
### 2.1 Додаємо групи
- Переходимо на http://localhost:3000/groups
- Додаємо групи які нас потенційно цікавлять, вказуємо гео-полігони груп
### 2.2 Конфігуруємо зону інтересу
Зона інтересу - це географічна область, повідомлення з якої нас цікавлять. `telegram-ingestion-service` буде передавати далі в пайплайн тільки повідомлення з груп, які входять в одну з браних зон інтересу
- Переходимо на http://localhost:3000/pipeline
- Малюємо потрібну зону на карті
- Тиснемо "Save Zone"
### 2.3 (ТИМЧАСОВО) щоб скинути кеш груп і зон інтересу на telegram-ingestion-service - перезавантажуємо додаток
### 2.4 Спостерігаємо за дашбордом
- Переходимо на http://localhost:3000/
- В залежності від джерела телеграм-повідомлень - або чекаємо, поки на UI з'являться нотифікації, або пишемо повідомлення у відповідні групи

