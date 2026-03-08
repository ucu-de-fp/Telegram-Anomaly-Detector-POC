# Lab Monitoring System

A functional reactive data processing pipeline for monitoring Telegram groups with anomaly detection.

## Architecture

- **telegram-ingestion-service**: Reactive Spring service that generates mock Telegram data stream
- **anomaly-detection-service**: Reactive Java service for keyword-based anomaly detection
- **admin-api-service**: Spring MVC service for managing Telegram groups
- **notification-service**: Spring WebFlux service for broadcasting notifications via SSE
- **frontend**: React-based admin UI with dark theme

## Tech Stack

- Java 25
- Spring Boot 4.0.2
- React
- PostgreSQL 18 with PostGIS
- RabbitMQ
- Docker & Docker Compose

## Quick Start

1. Run once to initialize telegram ingestion service (first run might fail)
```bash
APP_ENV=dev docker compose run --build --rm telegram-ingestion-telethon
```

2. Run the whole setup
```bash
APP_ENV=dev docker-compose up
APP_ENV=dev docker-compose up --build
APP_ENV=dev docker compose --parallel 8 up --build
docker-compose build --parallel
```

Access the frontend at: http://localhost:3000

## Services

- Frontend: http://localhost:3000
- Group Management API: http://localhost:8081
- Notification Service: http://localhost:8082
- Telegram Ingestion: http://localhost:8083
- Anomaly Detection: http://localhost:8084

## Database

- PostgreSQL with PostGIS: localhost:5432
- Databases: `admin`, `notification`
