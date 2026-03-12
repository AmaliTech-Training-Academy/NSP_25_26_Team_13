# LogStream

A centralised log aggregation and analytics platform built for AmaliTech NSP 25/26 — Team 13.

## Overview

LogStream ingests, stores, searches, and analyses structured log entries from any application.
It exposes a REST API for log submission and querying, an analytics layer backed by pre-built
database views, and a Metabase BI dashboard for visualisation — all wired together via Docker Compose.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend API | Java 21 · Spring Boot 3.2.0 · Spring Security · Spring Data JPA |
| Database | PostgreSQL 16 (partitioned tables) |
| Data Engineering | Python 3.11 · psycopg2 · ETL pipeline (cron, every 15 min) |
| Analytics UI | Metabase v0.59.x |
| Build & Package | Maven 3.9 · Docker · Docker Compose |
| Auth | JWT (jjwt 0.12.3) |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI |

## Quick Start (Docker Compose)

```bash
# 1. Clone
git clone <repo-url>
cd NSP_25_26_Team_13

# 2. Create your env file
cp .env.example .env
# Open .env and fill in DB_NAME, DB_USERNAME, DB_PASSWORD, JWT_SECRET

# 3. Start all services
docker compose up --build -d

# 4. Verify
docker compose ps
```

| Service | URL |
|---|---|
| Backend REST API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Metabase Dashboard | http://localhost:3000 |

> Default ports are controlled by `BACKEND_PORT` and `METABASE_PORT` in your `.env`.

## Documentation

| Doc | Description |
|---|---|
| [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) | Full step-by-step local development setup |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Docker Compose deployment guide |

## Team

| Area | Responsibility |
|---|---|
| A | Log Ingestion · Bulk Import |
| B | Search · Retention Policies |
| C | Auth · Analytics |
