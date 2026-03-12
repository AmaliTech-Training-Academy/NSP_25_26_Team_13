# LogStream Observability Dashboard — Setup & Documentation

This guide explains how to set up the **LogStream Observability Dashboard** using PostgreSQL and Metabase. Follow each step carefully to ensure the dashboard works correctly.

---

## PART 1 — LOAD THE VIEWS INTO POSTGRESQL

**Step 1 — Load SQL Views**

Open a terminal and run:

```bash
psql -U postgres -d logstream -f analytics/views.sql
```

* You should see 13 lines: `CREATE VIEW`.
* If errors occur, fix them before continuing.

**Using pgAdmin:**

1. Open pgAdmin → expand your server → right-click your database → **Query Tool**
2. Click the folder icon → open `analytics/views.sql`
3. Click **Run** (F5)
4. Check the **Messages** tab → should say `Query returned successfully`.

**Step 2 — Verify Views**

```bash
psql -U postgres -d logstream -c "\dv vw_*"
```

* All 13 views starting with `vw_` should appear.
* Re-run `views.sql` if any are missing.

---

## PART 2 — CONNECT METABASE TO POSTGRESQL

**Step 3 — Open Metabase**

```
http://localhost:3000
```

**Step 4 — Admin Panel**

* Click **gear icon ⚙** → **Admin settings**

**Step 5 — Add Database**

* Navigate: **Databases** → **Add database**
* Fill the form:

| Field         | Value         |
| ------------- | ------------- |
| Database type | PostgreSQL    |
| Display name  | LogStream     |
| Host          | localhost     |
| Port          | 5432          |
| Database name | logstream     |
| Username      | postgres      |
| Password      | your password |

* Click **Save** → ensure green **Successfully connected**.

**Step 6 — Sync Schema**

* In Admin → Databases → select **LogStream**
* Click **Sync database schema now**
* Wait 10 seconds → **Browse data** → Public → check all `vw_` tables appear.

---

## PART 3 — CREATE THE DASHBOARD

**Step 7 — New Dashboard**

1. Home → **+ New** → **Dashboard**
2. Name it: `LogStream Observability`
3. Click **Create** → blank dashboard appears

---

## PART 4 — BUILD THE PANELS

### General Steps for Each Panel

1. Click **+ Add a question** → **New question**
2. Choose **LogStream → Public → view_name**
3. Configure chart as described below
4. **Save** → set title → add to dashboard

---

### SECTION A — SYSTEM SNAPSHOT

**Panel A-1 — Service Health Overview**

* View: `vw_service_health_dashboard`
* Type: Table
* Conditional formatting:

  * `status`: CRITICAL → Red, DEGRADED → Orange, HEALTHY → Green
  * `error_rate_24h`: >10 → Red, >5 → Orange
  * `errors_last_1h`: >10 → Red
* Full-width

**Panel A-2 — Error Rate per Service**

* View: `vw_error_rate_24h`
* Type: Bar
* X: `service_name`
* Y: `error_rate_percent`
* Goal lines: 5 (SLO), 10 (Critical)
* Half-width

**Panel A-3 — Warning Rate per Service**

* View: `vw_warn_rate_24h`
* Type: Bar
* X: `service_name`, Y: `warn_rate_percent`
* Goal line: 10 → Watch threshold
* Bar color: Orange
* Half-width, beside A-2

---

### SECTION B — TRAFFIC & VOLUME

**Panel B-1 — Hourly Log Volume Timeline**

* View: `vw_volume_trends_hourly`
* Type: Line
* X: `hour`, Y: `log_count`, Series: `level`
* Smooth lines, legend top, custom colors
* Full-width

**Panel B-2 — Log Level Mix Donut**

* View: `vw_level_distribution`
* Type: Pie → Donut style
* Dimension: `level`, Measure: `log_count`
* Show total, colors match levels
* Quarter width

**Panel B-3 — Daily Log Volume by Service**

* View: `vw_volume_trends_daily`
* Type: Area
* X: `day`, Y: `log_count`, Series: `service_name`
* Stack, smooth lines
* Three-quarter width, beside B-2

**Panel B-4 — Top Noisy Services**

* View: `vw_top_noisy_services`
* Type: Bar
* X: `service_name`, Y: `total_logs`
* Show values on bars
* Quarter width, beside B-3

---

### SECTION C — ANOMALY & SIGNALS

**Panel C-1 — Error Spike Detection**

* View: `vw_error_spike_detection`
* Type: Table
* Conditional formatting:

  * `spike_status`: CRITICAL → Red, ELEVATED → Orange, NORMAL → Green
  * `spike_ratio`: >3 → Red, >1.5 → Orange
* Half-width


CREATE OR REPLACE VIEW vw_errors_per_hour AS
SELECT
    DATE_TRUNC('hour', timestamp) AS hour,
    service_name,
    COUNT(*) FILTER (WHERE level = 'ERROR') AS error_count
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY hour, service_name
ORDER BY hour;

**Panel C-2 — Mean Time Between Errors**

* View: `vw_mtbe_per_service`
* Type: Bar → Horizontal
* X: `avg_minutes_between_errors`, Y: `service_name`
* Half-width, beside C-1

**Panel C-3 — Silent Services**

* View: `vw_silent_services`
* Type: Table
* Conditional formatting: `minutes_silent`: >30 → Red, >15 → Orange
* Full-width

---

### SECTION D — PATTERNS & HISTORY

**Panel D-1 — Activity Heatmap**

* View: `vw_activity_heatmap`
* Type: Pivot Table
* Rows: `day_of_week`, Columns: `hour_of_day`, Values: `log_count`
* Conditional formatting: White → Amber
* Half-width

**Panel D-2 — Level Distribution per Service**

* View: `vw_level_distribution`
* Type: Bar → Stack, 100% stacked
* X: `service_name`, Y: `log_count`, Series: `level`
* Half-width, beside D-1

**Panel D-3 — Top 10 Error Messages**

* View: `vw_common_errors_top10`
* Type: Horizontal Bar
* X: `occurrences`, Y: `message`
* Show values, wide column
* Full-width

**Panel D-4 — Live Error Feed**

* View: `vw_recent_critical_events`
* Type: Table
* Format timestamp: `YYYY-MM-DD HH:mm:ss`
* Wide message column
* Full-width

---

## PART 5 — ADD SERVICE FILTER

**Step 8 — Add Filter**

* Dashboard → ✏ Edit → funnel icon → Add filter → Text/Category → Label: `Service Name` → Done

**Step 9 — Wire Filter**

* Connect filter to `service_name` column for all panels **except** `vw_silent_services`

**Step 10 — Default**

* Leave blank → All services
* Click **Done** → Save

---

## PART 6 — SET AUTO-REFRESH

**Step 11 — Refresh Interval**

* Click 🕐 → select **1 minute**
* Dashboard auto-refreshes every 60 seconds

---

## PART 7 — VERIFY EVERYTHING

**Step 12 — Test Live Update**

1. POST an ERROR log to backend API
2. Confirm received in terminal
3. Wait 60 seconds
4. Check `vw_recent_critical_events` → new error at top
5. Check `vw_error_spike_detection` → status updated


