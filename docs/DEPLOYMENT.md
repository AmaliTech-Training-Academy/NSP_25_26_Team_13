# Deployment Guide

This guide covers deploying LogStream using Docker Compose on a Linux server (e.g. an AWS EC2 instance).

---

## Prerequisites

| Tool | Version |
|---|---|
| Docker | 24+ |
| Docker Compose | v2 (`docker compose`) |
| Git | any |

The server must be able to reach:
- Docker Hub (to pull `postgres:16-alpine`, `metabase/metabase:v0.59.x`)
- Your container registry (if using pre-built images via CI/CD)

---

## 1. Get the Code on the Server

```bash
git clone <repo-url>
cd NSP_25_26_Team_13
```

Or copy the repository via `scp` / `rsync` if the server has no internet access to GitHub.

---

## 2. Configure Environment Variables

```bash
cp .env.example .env
```

Edit `.env` with production values. Every variable **must** be set — no defaults are used in the `prod` Spring profile.

```dotenv
# PostgreSQL
DB_NAME=logstream_db
DB_USERNAME=logstream_user
DB_PASSWORD=<strong-random-password>
DB_PORT=5432

# Backend (Spring Boot)
JWT_SECRET=<random-string-minimum-32-characters>
SPRING_PROFILES_ACTIVE=prod

# Ports exposed on the host
BACKEND_PORT=8080
METABASE_PORT=3000
```

### Security checklist before going live

- [ ] `DB_PASSWORD` is a strong, unique value (not the example placeholder)
- [ ] `JWT_SECRET` is at least 32 characters and randomly generated
- [ ] `.env` is **not** committed to version control (it is in `.gitignore`)
- [ ] Only required ports are open in the server firewall / security group

---

## 3. Build and Start All Services

```bash
docker compose up --build -d
```

Docker Compose starts services in dependency order:

```
postgres (healthy) → backend → data-engineering
                  → metabase
```

Check that everything came up:

```bash
docker compose ps
```

Expected output — all services should be `Up` or `Up (healthy)`:

```
NAME                        STATUS
logstream-postgres          Up (healthy)
logstream-backend           Up
logstream-data-engineering  Up
logstream-metabase          Up
```

---

## 4. Database Initialisation

On the **first startup** of a fresh volume, PostgreSQL automatically runs the init scripts:

| Script | Purpose |
|---|---|
| `01_schema_ddl.sql` | Tables: partitioned `log_entries`, `users`, `retention_policies` |
| `02_analytics.sql` | Views for Metabase (`vw_error_rate_24h`, hourly counts, etc.) |
| `03_metabase.sql` | Metabase internal config seed |
| `ensure_metabase_db.sh` | Creates the `metabase` database if it doesn't exist (runs every startup) |

The backend seeds initial data (`data.sql`) on application startup via Spring Boot's SQL initialiser.

> **Re-initialisation**: init scripts run only on a fresh volume. To re-run them, destroy the volume — this **deletes all data**:
> ```bash
> docker compose down -v
> docker compose up --build -d
> ```

---

## 5. Access Points

| Service | URL |
|---|---|
| Backend REST API | `http://<server-ip>:8080` |
| Swagger UI | `http://<server-ip>:8080/swagger-ui/index.html` |
| OpenAPI JSON spec | `http://<server-ip>:8080/v3/api-docs` |
| Metabase Dashboard | `http://<server-ip>:3000` |

Ports are controlled by `BACKEND_PORT` and `METABASE_PORT` in `.env`.

---

## 6. Health Checks

### Check service health

```bash
docker compose ps
docker compose logs --tail=50 backend
docker compose logs --tail=50 postgres
```

### Backend readiness probe

```bash
curl -f http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

### PostgreSQL probe

```bash
docker compose exec postgres pg_isready -U $DB_USERNAME -d $DB_NAME
```

---

## 7. Updating Services

### Pull latest code and rebuild

```bash
git pull origin main
docker compose up --build -d
```

Docker Compose rebuilds only changed images and replaces running containers.
PostgreSQL data is preserved in the `pgdata` named volume.

### Update a single service

```bash
# Rebuild and restart the backend only
docker compose up --build -d backend
```

---

## 8. Restarting and Stopping

```bash
# Restart a single service
docker compose restart backend

# Stop all services (data preserved)
docker compose down

# Stop and remove all containers + volumes (destructive — deletes all data)
docker compose down -v
```

---

## 9. Viewing Logs

```bash
# All services (stream live)
docker compose logs -f

# Single service
docker compose logs -f backend
docker compose logs -f postgres
docker compose logs -f data-engineering
docker compose logs -f metabase
```

---

## 10. Running Ad-hoc Data Engineering Tasks

The `data-engineering` container runs the ETL pipeline automatically every 15 minutes.
To trigger tasks manually:

```bash
# Generate synthetic log data
docker compose exec data-engineering python scripts/data_generator.py --count 10000 --days 30

# Run the ETL pipeline immediately
docker compose exec data-engineering python scripts/etl_pipeline.py
```

---

## 11. Metabase First-Time Setup

1. Open `http://<server-ip>:3000`
2. Complete the setup wizard
3. When asked to connect a database, enter:
   - **Database type**: PostgreSQL
   - **Host**: `postgres` (Docker internal hostname)
   - **Port**: `5432`
   - **Database name**: value of `DB_NAME` from `.env`
   - **Username / Password**: `DB_USERNAME` / `DB_PASSWORD`
4. Metabase pre-built views (`vw_error_rate_24h`, hourly log counts, etc.) will appear under the connected database

---

## Troubleshooting

### Backend fails to start — `Connection refused` to PostgreSQL

The backend waits for the postgres healthcheck to pass. If it keeps restarting:

```bash
docker compose logs postgres
```

- Confirm `DB_PASSWORD` in `.env` is correct
- Confirm the postgres container is `Up (healthy)` in `docker compose ps`

### `FATAL: password authentication failed`

The `DB_PASSWORD` in `.env` does not match what the postgres volume was initialised with.
Reset the volume (destructive):

```bash
docker compose down -v && docker compose up --build -d
```

### Port already in use

Change `BACKEND_PORT` or `METABASE_PORT` in `.env` to a free port, then restart:

```bash
docker compose down
docker compose up -d
```

### Backend returns 401 on all requests

Check that `JWT_SECRET` in `.env` is at least 32 characters. Short secrets cause jjwt initialisation to fail silently.

### Metabase shows blank dashboard after database connect

Trigger a schema sync from the Metabase admin panel:
**Settings → Databases → logstream → Sync database schema now**

Or via the API:

```bash
# Get session token
TOKEN=$(curl -s -X POST http://localhost:3000/api/session \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@example.com","password":"yourpassword"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# Trigger sync (replace DB_ID with the numeric ID from Metabase)
curl -s -X POST http://localhost:3000/api/database/<DB_ID>/sync_schema \
  -H "X-Metabase-Session: $TOKEN"
```

### Out of disk space

Named volumes accumulate data over time. Check usage:

```bash
docker system df
docker volume ls
```

Remove unused volumes:

```bash
docker volume prune
```

> Only prune volumes that are **not** in use. The `pgdata` volume holds all log data.
