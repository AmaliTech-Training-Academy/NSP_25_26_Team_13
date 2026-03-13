# LogStream Centralized Technical Documentation

Version: 1.0  
Last Updated: 2026-03-12  
Scope: Backend, Data Engineering, QA, DevOps, and Deployment

---

## 1. Project Overview

### System Description
LogStream is a centralized log aggregation and analysis platform that ingests structured logs from multiple services, stores them in PostgreSQL, and provides search, analytics, and service-health monitoring through REST APIs and dashboards.

### Problem Statement
Distributed systems generate large volumes of logs across many services. Without centralized ingestion and analysis, incident response is slow, observability is fragmented, and trend analysis becomes expensive.

### Goals of the Platform
- Provide reliable, high-throughput ingestion for single events, batch payloads, and files.
- Support operational querying by service, level, keyword, and time range.
- Expose service-level analytics (error rates, frequent errors, volume trends).
- Monitor health indicators per service for fast issue detection.
- Provide reproducible local and cloud deployment workflows.

### Core Capabilities
- Log ingestion APIs: single, batch, CSV/JSON import.
- Search APIs: filter + pagination + time-bounded keyword queries.
- Analytics APIs: 24h error rates, top errors, volume time series.
- Health dashboard API: status by service (GREEN/YELLOW/RED/UNKNOWN).
- Retention policy management and cleanup execution.
- Synthetic data generation and scheduled ETL aggregation.

### High-Level Architecture Summary
- Backend: Spring Boot API for auth, ingestion, search, analytics, retention, and health.
- Data layer: PostgreSQL 16 with partitioned log table and analytics indexes.
- Data engineering: Python ETL and generators run by cron (local) or EventBridge (AWS).
- Dashboarding: Metabase connected to PostgreSQL.
- Infrastructure: Docker Compose locally; AWS ECS/RDS/ALB/ECR/CodeDeploy/EventBridge in cloud.

### MVP Key Features
- JWT-based auth endpoints and role model (ADMIN/USER).
- `/api/logs` ingestion and `/api/logs/search` querying.
- `/api/analytics/*` and `/api/health/dashboard` monitoring APIs.
- Per-service retention policies with manual cleanup trigger.
- Automated ETL schedules (15-min standard, hourly, daily).

---

## 2. System Architecture

### Architecture Overview
LogStream is composed of three interacting planes:
- Application plane: Spring Boot backend APIs and server-rendered pages.
- Data plane: PostgreSQL partitioned storage + analytics/metrics tables.
- Processing plane: Python ETL/data jobs that aggregate and maintain operational datasets.

### Component Diagram Explanation
- Clients submit logs and query analytics through Backend API.
- Backend writes/reads PostgreSQL for logs, users, policies, and analytics results.
- Data-engineering jobs read raw logs and produce aggregated tables/views.
- Metabase reads PostgreSQL analytics data for dashboards.
- CI/CD builds/tests/pushes images and deploys ECS tasks/services.

### Data Flow (Ingestion -> Storage -> Analytics -> Dashboard)
1. Service/client sends log events to `/api/logs` (single/batch/import).
2. Backend validates payload and persists into `log_entries`.
3. Data-engineering jobs run on schedule:
- Standard (every 15 min): partition maintenance + health snapshot refresh.
- Hourly: writes `log_metrics_hourly`.
- Daily: writes `log_metrics_daily`.
- Retention: archives/deletes expired logs.
4. Backend analytics APIs query raw/aggregated data and return API responses.
5. Metabase visualizes prepared views/tables for operational dashboards.

---

## 3. Technology Stack

### Backend
- Java 21: modern LTS language/runtime for backend services.
- Spring Boot 3.x: fast API development with auto-configured enterprise components.
- Spring Security: authentication and authorization integration.
- JWT (jjwt): stateless tokens for API sessions and identity propagation.
- Spring Data JPA / Hibernate: repository pattern and ORM mapping.

Why: strong ecosystem, mature security model, and maintainable layered architecture.

### Database
- PostgreSQL 16: transactional reliability, partitioning support, indexing flexibility, and analytics-friendly SQL.

Why: supports both OLTP ingestion and heavy analytical reads in one engine.

### Data & Analytics
- Python 3.11: efficient scripting and ETL orchestration.
- Pandas: aggregation/transformation operations for health and trend metrics.

Why: fast iteration for data workflows and clear data-frame based processing.

### Infrastructure
- Docker: standardized packaging of backend/data/Metabase services.
- Docker Compose: local multi-service orchestration and dependency startup.
- GitHub Actions CI/CD: automated scanning, linting, testing, image build/push, and deployment.

Why: consistent dev/prod delivery model and repeatable operational automation.

### Testing
- Backend unit/integration tests (Maven/JUnit stack in backend module).
- Data engineering tests (pytest).
- QA API integration tests (RestAssured/TestNG in qa/api-tests).

Why: layered validation from unit behavior to full API workflow.

---

## 4. Backend Documentation

### Backend Architecture
LogStream backend follows a layered architecture:
- Controllers: request mapping, API contracts, and response formatting.
- Services: business logic (ingestion/search/analytics/retention/auth/health).
- Repositories: data access via JPA/native SQL.
- Models/DTOs: entity mapping and external API payload definitions.
- Security layer: JWT filter, auth manager, password encoder, exception handlers.

### Authentication & Authorization
- Auth endpoints:
- `POST /api/auth/register`
- `POST /api/auth/login`
- JWT token generated with email (subject), role claim, and expiration.
- Token delivered in response body and `jwt_token` HTTP-only cookie (`Max-Age: 86400`).
- Roles: `ADMIN`, `USER`.
- Password hashing: BCrypt.

Implementation note:
- Current `SecurityConfig` includes broad `permitAll` rules (including `/**`), so RBAC protection is weaker than intended and should be tightened for production hardening.

### API Design Principles
- REST-style endpoint grouping (`/api/auth`, `/api/logs`, `/api/analytics`, etc.).
- Validation:
- Bean validation (`@NotBlank`, `@Pattern`, `@Email`, `@Size`).
- File validation for import: not empty, <= 50MB, CSV/JSON only.
- Error handling:
- Centralized global exception handling with structured error payloads.
- Validation and auth exceptions mapped to proper HTTP status codes.
- Pagination:
- Request includes `page` (0-based) and `size`.
- Ingestion list endpoint caps size to 50.
- Search default: `page=0`, `size=20`.

### Log Ingestion
#### Single Log Ingestion
- Endpoint: `POST /api/logs`
- Payload mapped from `LogEntryRequest`.
- Automatically creates default retention policy for new services.

#### Bulk Log Ingestion
- Endpoint: `POST /api/logs/batch`
- Payload: list of log objects (`BatchLogRequest.logs`).
- Saves in batch and ensures retention policy existence for seen services.

#### CSV/JSON File Ingestion
- Endpoint: `POST /api/logs/import` (multipart)
- Supports `.csv` and `.json` file types.
- Async processing using configured file-processing executor.
- CSV expected columns: `id,timestamp,level,source,message,service_name,created_at`.

#### Validation Rules
- `serviceName`: required, `[a-zA-Z0-9-_]+`
- `level`: required, enum-compatible (`TRACE|DEBUG|INFO|WARN|ERROR`)
- `message`: required, 1..2000 chars
- `source`, `traceId`: optional, alphanumeric/hyphen/underscore
- `metadata`: optional map with validated keys/values
- `timestamp`: optional; defaults to now if absent

#### Supported Log Levels
- TRACE, DEBUG, INFO, WARN, ERROR

### Search & Query System
- Endpoints:
- `GET /api/logs/search` (query params)
- `POST /api/logs/search` (JSON body)
- Filters:
- `serviceName/service`
- `level`
- `startTime`, `endTime`
- `keyword` (message contains, case-insensitive)
- Pagination:
- `page`, `size` with descending timestamp sort.
- Query behavior:
- Service combines repository methods to avoid complex dynamic JPQL.
- Defaults time range to `Instant.EPOCH -> Instant.now()` if unspecified.

### Retention Policy
- Policy CRUD under `/api/retention`.
- Defaults:
- Default retention period: 30 days.
- New services auto-provisioned with `retentionDays=30`, `archiveEnabled=false`.
- Cleanup strategy:
- Custom policy cleanup per service via cutoff time.
- Services without explicit policy use default 30-day cleanup.
- Manual trigger endpoint: `POST /api/retention/cleanup`.

---

## 5. Analytics & Monitoring

### Provided Analytics
- Error rate per service over 24h (`/api/analytics/error-rate`).
- Most common error messages by service (`/api/analytics/common-errors`).
- Log volume trends by hour/day (`/api/analytics/volume`).

### Calculation Logic
- Error rate:
- For each service: `error_count / total_count * 100`, rounded to 2 decimals.
- Window defaults to last 24h.
- Common errors:
- Aggregates ERROR-level messages by frequency within time range.
- Default limit: 10, allowed range: 1..100.
- Volume trends:
- Uses `date_trunc('hour'|'day', created_at)` native SQL aggregation.
- Default range: last 7 days if not provided.
- Missing buckets are filled with zero counts.

### API Exposure
- APIs return typed DTO payloads wrapped in standard success envelopes for analytics/health controllers.

### Aggregation Strategies
- Live queries on `log_entries` for near-real-time operational metrics.
- ETL aggregation to `log_metrics_hourly`, `log_metrics_daily`, and `service_health_dashboard` for dashboard consumption and trend performance.

---

## 6. Health Dashboard

### Health Model
Endpoint: `GET /api/health/dashboard`

Per service returns:
- `service`
- `lastLogTime`
- `errorRate` (24h)
- `status`

### Status Indicators
- GREEN: error rate < 1%
- YELLOW: 1% to 5%
- RED: > 5%
- UNKNOWN: no logs in the 24h window

### Issue Detection Usage
- Detect silent services (UNKNOWN + stale `lastLogTime`).
- Detect noisy failures (RED/YELLOW escalation).
- Compare status trends with hourly/daily ETL metrics to confirm persistent or transient incidents.

---

## 7. Database Design

### Schema Design
Key tables:
- `log_entries` (partitioned by `timestamp`): raw logs.
- `users`: authentication and RBAC identity.
- `retention_policies`: per-service retention config.
- `logs_archive`: archived expired logs (retention script path).
- `log_metrics_hourly` / `log_metrics_daily`: ETL aggregates.

Operational note:
- DDL and JPA schema evolve together. Runtime JPA update mode may add columns (for example `archive_enabled`) beyond base DDL script.

### Indexing Strategy
- Service/time/level search:
- `idx_logs_search_lookup` on `(service_name, level, timestamp DESC)`
- Time-range scans:
- `idx_logs_volume_brin` on `timestamp` (BRIN)
- Keyword search:
- `idx_logs_msg_search` GIN index with `pg_trgm`
- User lookup:
- `idx_users_email`
- Archive history:
- `idx_archive_service_ts`, `idx_archive_level`

### Performance Considerations
- Range partitioning on `timestamp` supports partition pruning.
- ETL proactively creates daily partitions (today/tomorrow).
- BRIN minimizes index size for high-volume append-only log workloads.
- Aggregation tables reduce repeated expensive scans for dashboards.
- Pagination and bounded filters prevent unbounded result sets.

---

## 8. Data Engineering Pipeline

### ETL Pipeline Overview
Primary script: `data-engineering/scripts/etl_pipeline.py`

Modes:
- `standard` (every 15 min): partition management + service health refresh.
- `hourly`: aggregates previous full hour to `log_metrics_hourly`.
- `daily`: aggregates previous full day to `log_metrics_daily`.

### Aggregation Pipeline
- Extracts logs for target time window.
- Computes per-service totals and error counts.
- Uses idempotent loading by deleting existing period rows before insert.

### Handling Large Log Volumes
- Incremental time-window extraction.
- Partition-aware table management.
- SQLAlchemy + Pandas pipeline for controlled batch operations.

### Archival Strategy
- `retention_policy.py` supports disk-first archival:
- Select expired data.
- Optionally write CSV archive.
- Insert into `logs_archive` then delete from live table in transaction.

### Retention Handling
- Scheduled daily at 2 AM by cron in data-engineering container.
- Backend also offers retention cleanup API for service-level policy enforcement.

### Deriving Analytics from Raw Logs
- Raw logs (`log_entries`) feed:
- Health snapshot table (`service_health_dashboard`)
- Hourly/day metrics tables
- Backend analytics endpoints for error/message/volume calculations

---

## 9. Sample Data Generation

### Generator Overview
Primary script: `data-engineering/scripts/data_generator.py`

### Mock Service Simulation
Configured service set includes examples such as:
- auth-service
- payment-service
- order-service
- notification-service
- api-gateway

### Log Distribution Patterns
- Weighted level distributions with per-service baseline error rates.
- Business-hour-biased timestamp generation for realistic daily traffic curves.

### Error Simulation Logic
- Pre-placed spike windows (for example payment-service, order-service) with elevated ERROR weights.
- Pre-placed outage windows where logs are dropped to simulate service downtime.

### Realistic Pattern Features
- Time clustering around business hours.
- Service-specific error behavior.
- Mixed levels and message templates with randomized IDs.

### Example Generated Log
```json
{
  "id": "7d7c8fe0-4f44-4d8e-8f9f-9adfa0f4e2f8",
  "timestamp": "2026-03-10T13:29:57.441Z",
  "level": "ERROR",
  "source": "payment-service",
  "message": "Payment provider timeout for transaction 482",
  "service_name": "payment-service",
  "created_at": "2026-03-10T13:29:57.441Z"
}
```

---

## 10. DevOps & Infrastructure

### Docker Setup
- Backend container:
- Spring Boot API, env-based datasource/JWT configuration.
- Database container:
- PostgreSQL 16 with init scripts for schema/views/metabase setup.
- Data pipeline container:
- Python ETL + cron schedules + script execution runtime.

### Docker Compose
- Multi-container orchestration with dependency checks (`depends_on` + healthchecks).
- Shared network for service DNS names (`postgres`, `backend`, etc.).
- Environment-variable driven ports and credentials via `.env`.

### CI/CD Pipeline (GitHub Actions)
Workflow: `.github/workflows/ci.yml`

Main stages:
1. Secrets scan (Gitleaks).
2. Backend lint and data-engineering lint.
3. Backend build/test with PostgreSQL service.
4. Data-engineering test with PostgreSQL service.
5. Docker image builds.
6. QA API integration tests against live backend.
7. Deploy to `dev` on push to `dev` branch.
8. Deploy to `prod` on push to `main` (environment controlled).

Deployment actions include:
- Build/push images to ECR.
- Register ECS task definitions.
- Trigger CodeDeploy blue/green backend rollout.
- Update EventBridge schedules for data-engineering task definition revisions.

---

## 11. Environment Setup

### Prerequisites
- Git
- Java 21
- Maven 3.9+
- Docker + Docker Compose v2
- Python 3.11 (for local data-engineering execution outside containers)

### Repository Setup
1. Clone repository.
2. Create `.env` from `.env.example`.
3. Set DB credentials, JWT secret, backend/metabase ports.

### Run Locally (Recommended Full Stack)
1. `docker compose up --build -d`
2. Verify with `docker compose ps`
3. Access:
- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Metabase: `http://localhost:3000`

### Run Backend Only
1. Start PostgreSQL via Compose.
2. Run backend: `mvn spring-boot:run` in backend module.

### Run with Docker Compose (Operational)
- Start: `docker compose up --build -d`
- Stop: `docker compose down`
- Reset volumes: `docker compose down -v`

---

## 12. Testing Strategy

### Unit Testing Approach
- Backend services and configuration logic validated in unit tests.
- Data-engineering utilities and ETL logic validated with pytest.

### Integration Testing
- Backend integration tests executed against PostgreSQL service in CI.
- QA API tests run against started backend (`qa/api-tests`).

### Testing Responsibilities
- Developers: unit + module integration correctness.
- QA: cross-service API workflow validation, security scenarios, and regression checks.
- DevOps: CI reliability, quality gates, and artifact/deployment integrity.

### What Should Be Tested
- Ingestion validation for malformed payloads and missing fields.
- Search combinations (service/level/keyword/time) with pagination edges.
- JWT auth flows and role enforcement.
- Retention cleanup correctness and non-target data safety.
- ETL idempotency for hourly/daily reruns.

---

## 13. API Documentation Overview

All endpoints are available in Swagger/OpenAPI (`/swagger-ui/index.html`, `/v3/api-docs`).

### Authentication Endpoints
#### `POST /api/auth/register`
- Description: create user and return JWT.
- Required body fields: `fullName`, `email`, `password`
- Optional body fields: `role` (defaults to USER if invalid/omitted)
- Response body: `{ token, email, role }`

#### `POST /api/auth/login`
- Description: authenticate user and return JWT.
- Required body fields: `email`, `password`
- Response body: `{ token, email, role }`

### Log Ingestion Endpoints
#### `POST /api/logs`
- Description: ingest one structured log.
- Required body fields: `serviceName`, `level`, `message`
- Optional body fields: `metadata`, `source`, `traceId`, `timestamp`
- Response body: log entry DTO (id/service/timestamp/level/message/source/createdAt)

#### `POST /api/logs/batch`
- Description: ingest multiple logs.
- Required body fields: `logs` (non-empty array of valid log objects)
- Response body: batch summary with processed count.

#### `POST /api/logs/import`
- Description: import CSV/JSON logs asynchronously.
- Required multipart field: `file`
- Optional fields: none
- Response body: `{ "message": "Log import successful" }`

### Search Endpoints
#### `GET /api/logs/search`
- Description: query-param log search.
- Optional params: `service`, `level`, `startTime`, `endTime`, `keyword`, `page`, `size`
- Response body: paginated `LogEntryResponse` page.

#### `POST /api/logs/search`
- Description: body-based advanced log search.
- Optional body fields: `serviceName`, `level`, `startTime`, `endTime`, `keyword`, `page`, `size`
- Response body: paginated `LogEntryResponse` page.

### Analytics Endpoints
#### `GET /api/analytics/error-rate`
- Description: 24h per-service error rates.
- Required params: none
- Response body: list of `{ service, errorRate, errorCount, totalCount }`.

#### `GET /api/analytics/common-errors`
- Description: top error messages for a service/time range.
- Required params: `service`
- Optional params: `limit`, `startTime`, `endTime`
- Response body: list of `{ message, count }`.

#### `GET /api/analytics/volume`
- Description: service volume trend over time.
- Required params: `service`, `granularity` (`hour|day`)
- Optional params: `startTime`, `endTime`
- Response body: list of `{ timestamp, service, count }`.

### Health Dashboard Endpoint
#### `GET /api/health/dashboard`
- Description: service health indicators.
- Required params: none
- Response body: list of `{ service, lastLogTime, errorRate, status }`.

### Retention Endpoints
#### `GET /api/retention`
- Description: list policies.
- Response: array of retention policy objects.

#### `POST /api/retention`
- Description: create policy.
- Required fields: `serviceName`
- Optional fields: `retentionDays` (default 30), `archiveEnabled` (default false)

#### `PUT /api/retention/{serviceName}`
- Description: update policy for service.
- Body fields: `retentionDays`, `archiveEnabled`

#### `DELETE /api/retention/{serviceName}`
- Description: delete policy.

#### `POST /api/retention/cleanup`
- Description: trigger retention cleanup manually.

---

## 14. Security Considerations

### Authentication Strategy
- JWT token issuance at register/login.
- Role value embedded in token claim.
- HTTP-only cookie (`jwt_token`) set for web flows.

### JWT Handling
- Configurable secret and expiration (`jwt.secret`, `jwt.expiration`).
- Signature verification and claims parsing via JJWT.

### Input Validation
- Bean validation constraints on auth and ingestion payloads.
- File type and size checks for imports.
- Controlled parsing for timestamps and query parameters.

### API Protection
- Custom authentication entry point and access denied handlers are in place.
- Important hardening action: remove broad `permitAll` matchers and enforce endpoint-level role checks for protected routes (retention/admin/user management).

---

## 15. Deployment Guide

### Local Development Deployment
- Use Docker Compose for all services.
- Configure `.env` with local values.
- Bring stack up/down using Compose commands.

### Container Deployment
- Build backend and data-engineering images from module Dockerfiles.
- Run with required DB/JWT/secrets environment variables.
- Prefer health probes (`/actuator/health`) and structured logs.

### CI/CD Pipeline Deployment
- `dev` branch push: automatic dev deployment (ECR + ECS + CodeDeploy).
- `main` branch push: production deployment path with protected environment controls.
- Backend uses CodeDeploy blue/green for low-downtime release and traffic shift.

---

## 16. Project Collaboration Guidelines

### Git Branching Strategy
- `dev`: primary integration branch.
- `main`: production release branch.
- Feature branches: short-lived branches merged into `dev` through PRs.

### Pull Request Process
- Open PR into `dev` for feature/integration work.
- Require passing CI checks before merge.
- Keep PR scope focused (single objective per PR).

### Code Review Expectations
- Review for correctness, security, performance, and test coverage impact.
- Validate API contract changes and migration/schema implications.
- Confirm operational concerns (rollback path, observability, config changes).

### Task Tracking
- Track backend, data-engineering, QA, and DevOps tasks with clear ownership.
- Include acceptance criteria for each layer: API behavior, data correctness, and deployment readiness.

---

## Appendix: Operational Endpoints

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`
- Spring actuator health: `/actuator/health`

This document is intended as the single technical reference for engineering contributors working on LogStream.
