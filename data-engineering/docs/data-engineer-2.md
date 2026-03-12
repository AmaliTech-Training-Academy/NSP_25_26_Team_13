# Data Engineer 2 — Analysis & Sample Data

**Project:** LogStream — Centralized Log Aggregation and Analysis Platform  
**Team:** Team 13  
**Role:** Data Engineer 2 (Analysis & Sample Data)  
**Engineer:** Jeremiah Anku Coblah  
**Email:** jeremiah.coblah@amalitech.com  

---

## Overview

This document covers all deliverables completed by Data Engineer 2 for the LogStream platform. The scope covers sample data generation, data validation, analytics queries, and the Plotly observability dashboard.

---

## Folder Structure

```
data-engineering/
├── data/                        # Generated log JSON files
├── db/
│   └── dml/                     # Seed and insert scripts
├── docs/                        # Documentation
├── logs/                        # Generator run logs
├── scripts/
│   ├── dashboard/               # Plotly Dash dashboard
│   │   └── dashboard.py
│   ├── utils/
│   │   └── logger.py
│   ├── data_generator.py
│   ├── data_analytics.py
│   ├── validation.py
│   └── load_to_db.py
└── tests/                       # Unit tests
```

---

## Deliverables

### 1. Sample Data Generator (`scripts/data_generator.py`)

Generates realistic log data for 5 mock services whose field names exactly match the backend `log_entries` table schema:

```
id, timestamp, level, source, message, service_name, created_at
```

**Services simulated:**

- `api-gateway`
- `auth-service`
- `notification-service`
- `order-service`
- `payment-service`

**Key design decisions:**

- Error spikes and service outages are pre-placed as concrete datetime windows before any log is generated. This produces coherent, visible clusters in the timeline rather than random per-entry probability rolls.
- Per-service baseline error rates are configured individually. For example, `payment-service` has a 15% baseline error rate while `api-gateway` sits at 2%.
- Timestamps are weighted toward business hours using `_HOUR_WEIGHTS` to reflect realistic traffic patterns.
- The generator retries up to 3x the requested count to compensate for logs dropped by outage windows, keeping the output count reliable.
- No mutable global state. All runtime state (spike windows, outage windows) lives in local dicts passed through function arguments.

**CLI usage:**

```bash
python scripts/data_generator.py --count 10000 --days 30
python scripts/data_generator.py --services api-gateway payment-service --count 5000
python scripts/data_generator.py --out data/custom_output.json
```

**Output:** JSON file saved to `data/logs_YYYYMMDD_HHMMSS.json`

---

### 2. Error Patterns & Realistic Distribution

Error distribution is controlled at two levels:

**Baseline (per-service config):**

| Service             | Configured Error Rate |
|---------------------|----------------------|
| payment-service     | 15%                  |
| order-service       | ~8%                  |
| api-gateway         | 2%                   |
| auth-service        | ~5%                  |
| notification-service| ~5%                  |

**Spike windows:**  
Pre-placed windows where error weight temporarily escalates. During a spike, error probability rises to the configured `error_weight` and the remaining probability is redistributed across the other levels proportionally.

**Outage windows:**  
Pre-placed windows where a service emits no logs at all, simulating downtime. Any log whose timestamp falls inside an outage window is silently dropped.

---

### 3. Data Validation (`scripts/validation.py`)

The `validate()` function cleans a raw log DataFrame and returns `(clean_df, ValidationReport)`. All analytics functions operate only on validated data.

**Checks performed:**

| Check | Description |
|-------|-------------|
| Required columns | Raises immediately if any required field is missing |
| Empty rows | Completely null rows are removed |
| service_name null or blank | Rows with missing service identity are dropped |
| level normalisation | Values are uppercased and checked against `{TRACE, DEBUG, INFO, WARN, ERROR}` |
| message null or blank | Rows with no message content are dropped |
| timestamp parseable | ISO-8601 strings parsed with UTC awareness; failures dropped |
| Duplicate IDs | First occurrence kept, duplicates removed |
| Future timestamps | 60-second clock-skew tolerance; future-dated rows dropped |
| service_name length | Names exceeding 100 characters are dropped |
| Short messages | Messages under 3 characters are dropped |

Timestamps are stored as timezone-naive UTC throughout. `validate()` strips timezone info on ingestion so all downstream comparisons are naive-vs-naive.

---

### 4. Analytics Queries (`scripts/data_analytics.py`)

Pre-built analytics functions covering the core use cases required by the platform. All functions accept a cleaned DataFrame from `validate()`.

| Function | Description |
|----------|-------------|
| `error_rate_24h()` | Error rate per service for the last 24 hours |
| `common_errors_top_n()` | Top N most frequent ERROR messages, optionally per service |
| `volume_trends_hourly()` | Log volume broken down by hour and level |
| `volume_trends_daily()` | Log volume broken down by day and level |
| `warn_rate_24h()` | WARN rate per service for the last 24 hours |
| `level_distribution()` | Level breakdown per service as counts and percentage |
| `activity_heatmap()` | Log volume by hour-of-day x day-of-week |
| `error_spike_detection()` | Compares last 1-hour errors against 7-day daily average |
| `silent_services()` | Services with no logs in the last N minutes |
| `top_noisy_services()` | Services ranked by log volume in the last 24 hours |
| `mean_time_between_errors()` | Average minutes between consecutive ERROR logs per service |
| `recent_critical_events()` | Most recent ERROR logs across all services |
| `service_health_summary()` | Per-service health combining volume, error rate, and spike data |

**Service health status rules (mirrors `vw_service_health_dashboard`):**

```
errors_last_1h > 10     → CRITICAL
error_rate_24h > 10%    → DEGRADED
else                    → HEALTHY
```

**Spike detection thresholds:**

```
spike_ratio > 3.0   → CRITICAL
spike_ratio > 1.5   → ELEVATED
else                → NORMAL
```

Where `spike_ratio = (errors_last_1h * 24) / avg_daily_errors_7d`

---

### 5. Database Load Script (`scripts/load_to_db.py`)

Loads the most recently generated JSON file from `data/` into the PostgreSQL `log_entries` table via SQLAlchemy.

- Automatically selects the latest JSON file by modification time.
- Uses `to_sql` with `method="multi"` and `chunksize=1000` for efficient bulk inserts.
- Appends to the existing table (`if_exists="append"`), preserving any records already present.

```bash
python scripts/load_to_db.py
```

---

### 6. Plotly Observability Dashboard (`scripts/dashboard/dashboard.py`)

A full analytics dashboard built with Plotly Dash. Light-theme, data-forward design.

**Run:**

```bash
python scripts/dashboard/dashboard.py --data data/logs_YYYYMMDD_HHMMSS.json
# or, to use auto-generated sample data:
python scripts/dashboard/dashboard.py
```

Access at `http://127.0.0.1:8050`

**Dashboard sections:**

| Section | Charts |
|---------|--------|
| Snapshot | KPI cards — total events, errors, warnings, error rate, active services |
| Traffic | 7-day hourly volume timeline, log level donut, 30-day stacked daily volume |
| Reliability | Error rate by service (24h), top 10 error messages (30d) |
| Service Health | Per-service health table with status indicators |
| Patterns | Activity heatmap (hour x weekday), level composition by service |
| Signals | Log volume ranking, error spike detection, warn rate, mean time between errors, silent services |
| Live Error Feed | Most recent 25 ERROR events across all services |

**Service filter:** A dropdown in the header scopes all charts to a single service or shows all services.

**Design system:**

- Typography: DM Sans (labels) + JetBrains Mono (data/numbers)
- Colour palette: white base, slate greys, indigo primary, semantic red/orange/green/amber accents
- Per-service colour assignments are consistent across all charts

---

### 7. Metabase Dashboard

A Metabase dashboard has been configured as a complementary BI layer on top of the same PostgreSQL database. It provides shareable, no-code analytics views for non-technical stakeholders.

---

## How to Run the Full Pipeline

```bash
# 1. Generate sample data
python scripts/data_generator.py --count 10000 --days 30

# 2. Load data into PostgreSQL
python scripts/load_to_db.py

# 3. Launch the Plotly dashboard
python scripts/dashboard/dashboard.py --data data/<latest_file>.json
```

---

## Dependencies

```
pandas
plotly
dash
sqlalchemy
psycopg2-binary
```

Install with:

```bash
pip install -r requirements.txt
```

---

## Notes

- All analytics functions use the `_window()` helper as the single source of truth for time-windowing. No inline filtering is done in individual functions.
- The generator and analytics modules share the same `config/config.py` for service definitions, level weights, spike configs, and outage configs. Changes to service configuration propagate to both automatically.
- Logging throughout uses a shared `get_logger()` utility from `scripts/utils/logger.py` rather than `print()` statements.