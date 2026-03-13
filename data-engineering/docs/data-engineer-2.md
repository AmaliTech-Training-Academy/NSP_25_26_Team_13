# Data Engineer 2 — Analysis & Sample Data

**Project:** LogStream — Centralized Log Aggregation and Analysis Platform
**Team:** Team 13
**Role:** Data Engineer 2 (Analysis & Sample Data)
**Engineer:** Jeremiah Anku Coblah
**Email:** jeremiah.coblah@amalitech.com

---

## Overview

This document covers all deliverables completed by Data Engineer 2 for the LogStream platform. My scope as DE2 sits at the top of the data pipeline — I receive clean log data from the database (built by DE1), generate realistic sample data to populate it, run analytics on top of it, and surface everything through two dashboards: a Plotly Dash engineering dashboard and a Metabase BI dashboard.

The core question I was answering throughout this work is: **given thousands of log lines from five microservices, what actually matters to an engineer or a product owner who needs to know if the system is healthy?** Every analytics function, every SQL view, and every dashboard panel answers a specific version of that question.

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
│   ├── dashboard/
│   │   └── dashboard.py         # Plotly Dash observability dashboard
│   ├── utils/
│   │   └── logger.py
│   ├── data_generator.py        # Realistic log data generator
│   ├── data_analytics.py        # All analytics functions
│   ├── validation.py            # Data validation layer
│   └── load_to_db.py            # Loads generated data into PostgreSQL
└── tests/                       # Unit tests
```

---

## How to Run the Full Pipeline

```bash
# Step 1 — Generate sample data (10,000 logs spread over 30 days)
python scripts/data_generator.py --count 10000 --days 30

# Step 2 — Load the generated data into PostgreSQL
python scripts/load_to_db.py

# Step 3 — Launch the Plotly dashboard
python scripts/dashboard/dashboard.py --data data/<latest_file>.json

# Step 4 — Open the Metabase dashboard
# Navigate to http://localhost:3000 (or your server IP:3000)
```

---

## Dependencies

```
pandas
plotly
dash
sqlalchemy
psycopg2-binary
python-dotenv
```

Install with:

```bash
pip install -r requirements.txt
```

---

## Deliverable 1 — Sample Data Generator (`scripts/data_generator.py`)

### What it does

Generates realistic log data for 5 mock microservices. Every generated field exactly matches the backend `log_entries` table schema so the data loads directly without any transformation:

```
id, timestamp, level, source, message, service_name, created_at
```

**Services simulated:**

| Service | Baseline Error Rate | Notes |
|---|---|---|
| `payment-service` | 15% | Highest risk service — payment failures are common |
| `order-service` | 8% | Moderate errors from inventory and validation failures |
| `auth-service` | 5% | Token expiry and credential failures |
| `notification-service` | 3% | Mostly reliable, occasional delivery failures |
| `api-gateway` | 2% | Entry point — very low baseline errors |

### Why these error rates matter

Real microservice systems do not have uniform error rates. `payment-service` talks to external payment gateways that time out, `order-service` has complex validation logic that fails under edge cases, and `api-gateway` mostly proxies requests and rarely errors on its own. These per-service rates are configured in `config/config.py` so every analytics function and every SQL view reflects a believable system rather than random noise.

### Key design decisions

**Pre-placed spike and outage windows**

Before a single log is generated, the generator places concrete datetime windows into the timeline for error spikes and service outages. This means error clusters appear as real bursts at specific times — the kind of pattern you would see in a real incident — rather than as random per-log probability rolls that produce a flat, unrealistic distribution.

```
payment-service  spike 1: 2026-03-01 14:22 → 14:42  (20 min, 65% error rate)
payment-service  spike 2: 2026-03-07 09:11 → 09:31
order-service    spike 1: 2026-03-04 16:05 → 16:20
auth-service     outage:  2026-03-06 02:14 → 02:39  (25 min silence)
notification-service outage: 2026-03-09 11:30 → 12:00  (30 min silence)
```

These windows are what make panels like **Error Spike Detection** and **Silent Services** on the dashboard actually fire and show interesting data during a demo.

**Business-hours timestamp weighting**

Logs are not distributed evenly across 24 hours. The generator uses `_HOUR_WEIGHTS` — a 24-value array — to weight timestamps toward business hours (09:00–17:00) and suppress them overnight. This makes the **Activity Heatmap** show the realistic bright amber band across weekday business hours that you would expect from a real system.

**Retry headroom**

The generator runs up to 3× the requested log count in its loop to compensate for logs that are silently dropped by outage windows. This ensures that asking for `--count 10000` reliably produces close to 10,000 records even when services are silent for chunks of time.

### CLI usage

```bash
# Default: 10,000 logs across all 5 services, last 30 days
python scripts/data_generator.py

# Custom count and time window
python scripts/data_generator.py --count 50000 --days 90

# Specific services only
python scripts/data_generator.py --services api-gateway payment-service --count 5000

# Custom output file
python scripts/data_generator.py --out data/demo_data.json
```

**Output:** JSON file saved to `data/logs_YYYYMMDD_HHMMSS.json`

---

## Deliverable 2 — Data Validation (`scripts/validation.py`)

### What it does

Every analytics function operates on validated data. Before any analysis runs, `validate()` cleans the raw DataFrame and returns `(clean_df, ValidationReport)`. This is a hard contract — raw data never reaches an analytics function.

### Why this layer exists

The backend ingests logs from external services over HTTP. Those logs can arrive malformed — missing fields, unparseable timestamps, invalid log levels, duplicate IDs from retry storms, or messages from the future due to clock drift between services. Without a validation layer, these bad rows would silently corrupt analytics results. A service with 3 duplicate error records would appear twice as error-prone as it really is.

### Checks performed

| # | Check | Why it matters |
|---|---|---|
| 1 | Required columns present | Raises immediately — analytics cannot run without the core fields |
| 2 | Completely empty rows | Phantom rows from bulk import padding |
| 3 | `service_name` not null or blank | Every log must be attributable to a service |
| 4 | `level` normalised and validated | Mixed casing (`error`, `ERROR`, `Error`) would cause groupBy to miss records |
| 5 | `message` not null or blank | A log with no message has no diagnostic value |
| 6 | `timestamp` parseable as ISO-8601 | Unparseable timestamps break all time-windowed queries |
| 7 | No duplicate `id` values | Retry storms can send the same log event multiple times |
| 8 | No future timestamps (60s tolerance) | Clock skew between services should not count as future events |
| 9 | `service_name` under 100 characters | Guards against accidentally injected long strings |
| 10 | `message` at least 3 characters | Single-character messages are noise, not diagnostics |

**Timezone handling:** The generator emits ISO-8601 strings with `+00:00`. `validate()` parses them with `utc=True` then strips timezone info with `.dt.tz_localize(None)`. All downstream comparisons are therefore naive-vs-naive UTC — this single decision prevents a whole class of timezone comparison errors that would otherwise surface silently in `_window()`.

---

## Deliverable 3 — Analytics Queries (`scripts/data_analytics.py`)

### Architecture

All analytics functions share one central time-windowing helper:

```python
def _window(df, days=0, hours=0):
    cutoff = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(days=days, hours=hours)
    return df[df["timestamp"] >= cutoff]
```

This is the single source of truth for time filtering. No function filters timestamps inline — they all call `_window()`. This means changing the window logic (say, for timezone handling or daylight saving adjustments) is a one-line fix in one place.

---

### Analytics Functions — Explained

---

#### `error_rate_24h(df)` → Service Error Rate, Last 24 Hours

**What it calculates:** For each service: total logs, total ERROR logs, and `error_count / total_logs × 100` as a percentage. Sorted worst-first.

**Why it matters to the product owner:** This is the first number anyone looks at when something feels wrong. If `payment-service` has a 25% error rate in the last 24 hours, one in four payment operations is failing. This directly maps to revenue loss. The 24-hour window is intentional — it captures today's behaviour without being diluted by historical data from quieter periods.

**Dashboard panel:** A-2 — Error Rate per Service. The SLO line at 5% and Critical line at 10% give immediate context: green bars are fine, amber bars need watching, red bars need action now.

---

#### `warn_rate_24h(df)` → Warning Rate, Last 24 Hours

**What it calculates:** Same structure as error rate but for WARN level logs.

**Why it matters:** WARNs are leading indicators. A service whose warning rate spikes at 09:00 will often start throwing errors by 09:30. Monitoring WARNs gives the engineering team advance notice before users are affected. A high warn rate on `payment-service` often means the payment gateway is struggling — not yet failing, but about to.

**Dashboard panel:** A-3 — Warning Rate per Service.

---

#### `volume_trends_hourly(df)` → Hourly Log Volume by Level, Last 7 Days

**What it calculates:** Log count grouped by hour × level. One row per (hour, service, level) combination.

**Why it matters:** Traffic patterns reveal system health in ways that error rates alone cannot. A sudden drop in INFO volume from `api-gateway` at 14:00 when it was busy all morning is not an error — it is silence, which may mean the service crashed or was accidentally scaled to zero. A sudden spike in DEBUG volume might mean someone left verbose logging on in production, which is a performance problem. The 7-day window lets you compare today's pattern against the same time last week.

**Dashboard panel:** B-1 — Hourly Log Volume Timeline. Each log level is a separate coloured line so you can see whether an overall volume increase is driven by INFO (normal traffic growth) or ERROR (incident in progress).

---

#### `volume_trends_daily(df)` → Daily Log Volume by Service, Last 30 Days

**What it calculates:** Log count grouped by day × service. Used for the stacked area chart.

**Why it matters:** The 30-day view answers the strategic question: is the system growing? Is `order-service` processing more orders this week than last week? Are log volumes stable or drifting upward in a way that will hit storage limits? Day-over-day trends are also useful for capacity planning — if `payment-service` logs 50,000 events on a Monday and 8,000 on a Saturday, retention and storage policies should account for that variance.

**Dashboard panel:** B-3 — Daily Log Volume by Service (stacked area).

---

#### `level_distribution(df)` → Log Level Breakdown per Service, Last 30 Days

**What it calculates:** For each service × level combination: count and percentage of that service's total volume. A service with 30% ERROR logs has a fundamentally different profile than one with 1% ERROR logs.

**Why it matters:** The composition of a service's logs tells you as much as the count. Two services that each emit 10,000 logs per day are very different if one is 95% INFO and one is 40% ERROR. This function surfaces that composition so you can see at a glance which services are noisy-but-healthy versus noisy-and-broken.

**Dashboard panels:** B-2 — Log Level Mix donut (fleet-wide composition), D-2 — Level Distribution per Service stacked bar (per-service composition side by side).

---

#### `activity_heatmap(df)` → Log Volume by Hour × Weekday, Last 30 Days

**What it calculates:** Log count grouped by day-of-week × hour-of-day. Produces a 7×24 grid where each cell represents how busy the system is at that time.

**Why it matters:** This is one of the most valuable patterns for operations teams. It tells you when your system is busiest, which informs maintenance window scheduling, alerting sensitivity, and staffing. If the heatmap shows heavy activity from Monday–Friday 09:00–17:00 and quiet weekends, that is a normal B2B SaaS pattern. If you see a bright cell at 03:00 on Sundays, that is probably a batch job — and if it disappears one week, the batch job failed silently.

The amber colour scale (dark = quiet, bright = busy) makes this immediately readable without needing to read any numbers.

**Dashboard panel:** D-1 — Activity Heatmap.

---

#### `error_spike_detection(df)` → Anomalous Error Spikes vs 7-Day Baseline

**What it calculates:**

```
spike_ratio = (errors_last_1h × 24) / avg_daily_errors_7d
```

Multiplying `errors_last_1h` by 24 converts the 1-hour count to a daily-equivalent so it is directly comparable to the 7-day average. Thresholds:

```
spike_ratio > 3.0  → CRITICAL   (3× the normal daily rate in just one hour)
spike_ratio > 1.5  → ELEVATED   (1.5× — elevated but not yet critical)
else               → NORMAL
```

**Why it matters:** A raw error count is not enough on its own. `payment-service` normally generates 50 errors per day, so 8 errors in the last hour is completely normal (8 × 24 = 192, close to baseline). But if `api-gateway` normally generates 5 errors per day and suddenly produces 15 errors in one hour (15 × 24 = 360, ratio = 72×), that is a critical incident even though the absolute number is smaller than `payment-service`'s normal output.

Spike detection is the difference between alert fatigue and meaningful alerting. It normalises each service against its own history, not against a fixed global threshold.

**Dashboard panel:** C-1 — Error Spike Detection.

---

#### `silent_services(df)` → Services That Stopped Logging

**What it calculates:** Any service whose most recent log is older than 10 minutes. Returns `service_name`, `last_log_at`, and `minutes_silent`.

**Why it matters:** Silence is an incident. A service that stops logging has either crashed, lost its connection to the log ingestion API, been accidentally shut down, or encountered a fatal error that prevented it from writing any output. Traditional error-rate monitoring cannot detect this — if a service emits zero logs, its error rate is technically 0%, which looks healthy.

The 10-minute threshold is intentional. It is long enough to ignore normal quiet periods between requests but short enough to catch outages before users start filing support tickets.

**Dashboard panel:** C-3 — Silent Services. This panel is deliberately not wired to the service filter variable because you always want to see the full fleet — if you are filtering to `payment-service` and `auth-service` silently dies, you should still see it.

---

#### `mean_time_between_errors(df)` → Reliability Metric per Service, Last 7 Days

**What it calculates:** For each service, the average number of minutes between consecutive ERROR logs. Uses `LAG()` logic to find the gap between each error and the previous one, then averages those gaps.

**Why it matters:** This is a reliability metric borrowed from hardware engineering (Mean Time Between Failures). A service with an MTBE of 120 minutes errors roughly twice per working day — that is manageable. A service with an MTBE of 2 minutes is in constant distress regardless of what its error rate percentage looks like.

Higher is better. Sorting by MTBE ascending (least stable at top) means the services that need the most attention appear first.

**Dashboard panel:** C-2 — Mean Time Between Errors. The colour gradient from red (low MTBE = unstable) to green (high MTBE = stable) makes the ranking immediately readable.

---

#### `top_noisy_services(df)` → Log Volume Ranking, Last 24 Hours

**What it calculates:** Services ranked by total log count in the last 24 hours, with each service's percentage of total fleet volume.

**Why it matters:** Log volume is a cost driver. In a real production system, logs flow into storage (PostgreSQL, S3, or a dedicated log store) that charges by volume. A service that produces 60% of all logs but only handles 10% of business transactions is probably misconfigured — debug logging left on, or logging every single database query. This function makes that visible so the team can set appropriate retention policies per service and tune log verbosity.

**Dashboard panel:** B-4 — Top Noisy Services.

---

#### `common_errors_top_n(df)` → Top N Most Frequent Error Messages, Last 30 Days

**What it calculates:** The N most repeated ERROR messages across the fleet, grouped by service and message text. Sorted by occurrence count descending.

**Why it matters:** If `NullPointerException in OrderProcessor.validate()` appears 847 times in 30 days, that is a bug that has been silently failing for a month. Without this aggregation, it is buried in thousands of individual log lines. This function surfaces the highest-priority bugs by their actual frequency rather than by when an engineer happened to notice them.

**Dashboard panel:** D-3 — Top 10 Error Messages. The service prefix (`payment-service › Payment gateway unavailable`) means you know immediately which team owns the fix.

---

#### `recent_critical_events(df)` → Live Error Feed

**What it calculates:** The 50 most recent ERROR logs across all services, sorted newest first.

**Why it matters:** When something is actively going wrong, on-call engineers need to see the raw events in real time — not aggregated, not summarised, but the actual error messages as they arrive. This is the operational view that complements all the aggregate views above. The 50-row limit keeps the panel fast even under high error volumes.

**Dashboard panel:** D-4 — Live Error Feed. Combined with the 1-minute auto-refresh on both dashboards, this behaves like a near-real-time incident feed.

---

#### `service_health_summary(df)` → Per-Service Health Status

**What it calculates:** Combines 24-hour volume and rate metrics with a 1-hour error window to produce a single status per service:

```
errors_last_1h > 10          → CRITICAL
error_rate_24h > 10%         → DEGRADED
else                         → HEALTHY
```

**Why it matters:** This is the single-glance view for a product owner or engineering manager who needs to know in 5 seconds whether the system is healthy. The two-tier logic is deliberate: `errors_last_1h` catches sudden acute incidents (the service is on fire right now), while `error_rate_24h` catches chronic degradation (the service has been struggling all day, even if the last hour happened to be quiet).

**Dashboard panel:** A-1 — Service Health Overview. This is the first panel on both dashboards because it answers the most important question first.

---

## Deliverable 4 — Database Load Script (`scripts/load_to_db.py`)

Loads the most recently generated JSON file from `data/` into the PostgreSQL `log_entries` table using SQLAlchemy.

- Automatically selects the latest file by modification time — no need to specify a filename manually.
- Uses `to_sql` with `method="multi"` and `chunksize=1000` for efficient bulk inserts that do not lock the table.
- Uses `if_exists="append"` so existing records are preserved.

```bash
python scripts/load_to_db.py
```

---

## Deliverable 5 — SQL Analytics Views (`analytics/views.sql`)

All 13 analytics functions have direct SQL equivalents as PostgreSQL views. These views serve the Metabase dashboard and allow any SQL-capable tool to query pre-built analytics without needing the Python layer.

The views are designed to be live — they use `NOW()` so every query always returns current data, and the Metabase dashboard auto-refreshes every minute against them.

**Additional KPI views built for the dashboard header tiles:**

| View | Purpose |
|---|---|
| `vw_total_events_24h` | Total log volume in last 24 hours |
| `vw_total_warnings_24h` | Total WARN count in last 24 hours |
| `vw_total_errors_24h` | Total ERROR count in last 24 hours |
| `vw_total_services` | Count of distinct services currently reporting |
| `vw_overall_error_rate_24h` | Fleet-wide error rate across all services |

Load all views into PostgreSQL:

```bash
psql -U postgres -d logstream -f analytics/views.sql
```

Verify all views are present:

```bash
psql -U postgres -d logstream -c "\dv vw_*"
```

---

## Deliverable 6 — Plotly Observability Dashboard (`scripts/dashboard/dashboard.py`)

A full engineering-grade analytics dashboard built with Plotly Dash. Designed for engineers who need deep visibility into system behaviour.

**Run:**

```bash
python scripts/dashboard/dashboard.py --data data/logs_YYYYMMDD_HHMMSS.json
```

Access at `http://127.0.0.1:8050`

| Section | Panels |
|---|---|
| Snapshot | KPI tiles — total events, errors, warnings, error rate, active services |
| Traffic | 7-day hourly volume timeline by level, log level donut, 30-day stacked daily volume |
| Reliability | Error rate by service (24h), top 10 error messages (30d) |
| Service Health | Per-service health table with CRITICAL / DEGRADED / HEALTHY status |
| Patterns | Activity heatmap (hour × weekday), level composition by service |
| Signals | Volume ranking, error spike detection, warn rate, MTBE, silent services |
| Live Error Feed | Most recent 25 ERROR events across all services |

**Service filter:** A dropdown in the header scopes all charts to a single service or shows the full fleet.

**Design system:** White base, slate greys, indigo primary, semantic red/orange/green/amber accents. Per-service colours are consistent across every chart so `payment-service` is always the same colour regardless of which panel you are reading.

---

## Deliverable 7 — Metabase Dashboard

A complementary BI dashboard built on top of the same PostgreSQL views. Designed for product owners and non-technical stakeholders who need the same analytics without needing to run Python.

The Metabase dashboard mirrors the Plotly dashboard panel-for-panel across four sections (A through D) and auto-refreshes every minute against live PostgreSQL data.

**Setup:**

```sql
-- Create the Metabase internal database (run once on the server)
CREATE DATABASE metabase;
```

Then connect Metabase to PostgreSQL (host: `postgres`, port: `5432`, database: `logstream`) and import the dashboard JSON from `analytics/logstream_grafana_v4.json`.

---

## Design Decisions Summary

| Decision | Rationale |
|---|---|
| Pre-placed spike/outage windows | Produces coherent, visible incidents in the timeline rather than statistical noise |
| Business-hours timestamp weighting | Makes heatmap and volume trends reflect realistic traffic patterns |
| Single `_window()` helper | All time filtering goes through one function — timezone bugs are fixed in one place |
| Timezone-naive UTC throughout | `validate()` strips timezone on ingestion; all downstream comparisons are naive-vs-naive, preventing a class of silent comparison errors |
| Validate-before-analytics contract | Raw data never reaches analytics functions; bad rows are reported and excluded rather than silently corrupting results |
| Per-service baseline error rates | Each service has a realistic individual error profile rather than a uniform fleet-wide rate |
| Spike ratio = hourly × 24 ÷ 7d-daily-avg | Normalises each service against its own history, not a fixed global threshold — prevents alert fatigue |
| Silent services not wired to service filter | You always need to see the full fleet for silence detection — filtering to one service would hide other services going dark |

---

## Notes

- All analytics functions use `_window()` as the single source of truth for time-windowing. No inline filtering is done in individual functions.
- The generator and analytics modules share the same `config/config.py` for service definitions, level weights, spike configs, and outage configs. Changes to service configuration propagate to both automatically.
- Logging throughout uses a shared `get_logger()` utility from `scripts/utils/logger.py` rather than `print()` statements, so all output is structured and level-controlled.
- The Python analytics layer (`data_analytics.py`) and the SQL views layer (`views.sql`) implement the same logic independently. This means the Plotly dashboard (Python) and the Metabase dashboard (SQL) always agree on numbers — they are two interfaces on the same analytical definitions, not two separate implementations that could drift apart.