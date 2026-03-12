# Local Development Setup

Follow these steps to get LogStream running on your machine from scratch.
Estimated time: **10–15 minutes**.

---

## Prerequisites

Install the following tools before you begin.

| Tool | Version | Download |
|---|---|---|
| Git | any | https://git-scm.com |
| Java (JDK) | **21** | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| Docker | 24+ | https://docs.docker.com/get-docker/ |
| Docker Compose | v2 (`docker compose`) | bundled with Docker Desktop |

Verify your installations:

```bash
java -version        # must show 21.x
mvn -version         # must show 3.9.x
docker --version
docker compose version
```

---

## 1. Clone the Repository

```bash
git clone <repo-url>
cd NSP_25_26_Team_13
```

---

## 2. Configure Environment Variables

```bash
cp .env.example .env
```

Open `.env` and fill in **every blank value**:

```dotenv
# PostgreSQL
DB_NAME=logstream_db
DB_USERNAME=logstream_user
DB_PASSWORD=yourpassword        # choose any strong password
DB_PORT=5432

# Backend
JWT_SECRET=your-secret-at-least-32-characters-long
SPRING_PROFILES_ACTIVE=dev

# Metabase
METABASE_PORT=3000

# Backend API port
BACKEND_PORT=8080
```

> **Never commit `.env` to version control.** It is already in `.gitignore`.

---

## 3. Start All Services with Docker Compose

This is the recommended way to run the full stack locally.

```bash
docker compose up --build -d
```

Docker Compose will:
1. Pull `postgres:16-alpine` and `metabase/metabase:v0.59.x` images
2. Build the Spring Boot backend image (`./backend/Dockerfile`)
3. Build the data-engineering image (`./data-engineering/Dockerfile`)
4. Start services in dependency order: **postgres → backend → data-engineering → metabase**
5. Run database init scripts automatically on the first startup (schema, analytics views, Metabase DB)

Check that all containers are healthy:

```bash
docker compose ps
```

All services should show `Up` or `Up (healthy)`.

---

## 4. Access Points

| Service | URL | Notes |
|---|---|---|
| Backend REST API | http://localhost:8080 | Resolves to `BACKEND_PORT` from `.env` |
| **Swagger UI** | http://localhost:8080/swagger-ui/index.html | Interactive API explorer |
| OpenAPI JSON | http://localhost:8080/v3/api-docs | Machine-readable spec |
| Metabase | http://localhost:3000 | Resolves to `METABASE_PORT` from `.env` |

---

## 5. Database Migrations

Database schema is managed automatically — **no manual migration commands are needed**.

On the **first startup** of a fresh volume, PostgreSQL runs the init scripts in order:

| Script | What it does |
|---|---|
| `data-engineering/db/ddl/schema_ddl.sql` | Creates extensions, partitioned `log_entries` table, `users`, `retention_policies` |
| `data-engineering/db/dml/analytics.sql` | Creates Metabase views (`vw_error_rate_24h`, etc.) |
| `data-engineering/db/dml/init_metabase.sql` | Seeds Metabase configuration |
| `devops/db/ensure_metabase_db.sh` | Creates the Metabase internal database if it doesn't exist (runs on every startup) |

> Init scripts only run on a **fresh volume**. If you need to re-run them, reset the database (see [Troubleshooting](#troubleshooting)).

The backend seeds initial data (`data.sql`) via Spring Boot's `spring.sql.init` on application startup.

---

## 6. Running the Backend Without Docker (IDE / Maven)

If you want to run just the Spring Boot backend against a local PostgreSQL instance:

### 6.1 Start PostgreSQL only

```bash
docker compose up postgres -d
```

### 6.2 Run the backend

```bash
cd backend
mvn spring-boot:run
```

This picks up environment variables from `.env` via the `spring-dotenv` library.
By default the `dev` profile is active, which connects to `localhost:5432`.

To use an explicit profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 6.3 Run tests

```bash
cd backend
mvn test
```

Tests use the `test` Spring profile and an H2 in-memory database — no running PostgreSQL needed.

---

## 7. Generating Sample Log Data

The data-engineering container runs the ETL pipeline automatically every 15 minutes.
To generate data on demand:

```bash
# Generate 10 000 logs spread across the last 30 days
docker compose exec data-engineering python scripts/data_generator.py --count 10000 --days 30

# Run the ETL pipeline immediately
docker compose exec data-engineering python scripts/etl_pipeline.py
```

---

## 8. Stopping and Cleaning Up

```bash
# Stop all containers (data is preserved)
docker compose down

# Stop and delete all data volumes (full reset)
docker compose down -v
```

---

## Troubleshooting

### `docker compose up` fails immediately — port already in use

Change `BACKEND_PORT` or `METABASE_PORT` in `.env` to a free port, then re-run `docker compose up -d`.

### Backend container keeps restarting

The backend waits for PostgreSQL to be healthy before starting. Watch the logs:

```bash
docker compose logs -f backend
```

Common causes:
- Wrong `DB_PASSWORD` in `.env` — must match `DB_PASSWORD` used by postgres container
- PostgreSQL port conflict — check if port `5432` is already in use by a local postgres

### `password authentication failed for user`

Your `DB_USERNAME` / `DB_PASSWORD` in `.env` do not match what PostgreSQL was initialised with.
Reset the database volume:

```bash
docker compose down -v
docker compose up --build -d
```

### Metabase shows "Unable to connect to database"

On first access to Metabase, complete the setup wizard and set the connection:
- **Host**: `postgres`
- **Port**: `5432`
- **Database name**: value of `DB_NAME` in your `.env`
- **Username / Password**: `DB_USERNAME` / `DB_PASSWORD` from `.env`

### Maven build fails — `UnsupportedClassVersionError`

Ensure `JAVA_HOME` points to JDK 21:

```bash
java -version    # must be 21
mvn -version     # shows the JDK in use
```

### Swagger UI returns 403

Ensure `SecurityConfig.java` permits `/swagger-ui/**` and `/v3/api-docs/**`. These paths are already whitelisted in the current config.
