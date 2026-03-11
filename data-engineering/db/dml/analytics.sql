-- views.sql
-- LogStream Analytics Views
-- Purpose : Pre-built Postgres views for Metabase dashboards.
--           Each view maps directly to a Metabase panel.
--           All views read from the log_entries partitioned table.
--
-- Usage   : Run once against your database to create all views.
--           Metabase connects and queries them like normal tables.
--           Re-run with CREATE OR REPLACE VIEW to update any view.
--
-- Indexes used:
--   idx_logs_search_lookup  → (service_name, level, timestamp DESC)
--   idx_logs_volume_brin    → BRIN on timestamp



-- VIEW 1: Error Rate per Service — Last 24 Hours
-- Metabase panel : Bar chart  (x=service_name, y=error_rate_percent)
CREATE OR REPLACE VIEW vw_error_rate_24h AS
SELECT
    service_name,
    COUNT(*)                                                    AS total_logs,
    COUNT(*) FILTER (WHERE level = 'ERROR')                     AS error_count,
    ROUND(
        COUNT(*) FILTER (WHERE level = 'ERROR') * 100.0
        / NULLIF(COUNT(*), 0),
        2
    )                                                           AS error_rate_percent
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY service_name
ORDER BY error_rate_percent DESC;


-- VIEW 2: WARN Rate per Service — Last 24 Hours
-- Metabase panel : Bar chart (x=service_name, y=warn_rate_percent)
CREATE OR REPLACE VIEW vw_warn_rate_24h AS
SELECT
    service_name,
    COUNT(*)                                                    AS total_logs,
    COUNT(*) FILTER (WHERE level = 'WARN')                      AS warn_count,
    ROUND(
        COUNT(*) FILTER (WHERE level = 'WARN') * 100.0
        / NULLIF(COUNT(*), 0),
        2
    )                                                           AS warn_rate_percent
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY service_name
ORDER BY warn_rate_percent DESC;



-- VIEW 3: Top 10 Most Common Errors — Last 30 Days
-- Metabase panel : Table (service_name, message, occurrences)
CREATE OR REPLACE VIEW vw_common_errors_top10 AS
SELECT
    service_name,
    message,
    COUNT(*) AS occurrences
FROM log_entries
WHERE
    level     = 'ERROR'
    AND timestamp >= NOW() - INTERVAL '30 days'
GROUP BY service_name, message
ORDER BY occurrences DESC
LIMIT 10;



-- VIEW 4: Hourly Log Volume per Service — Last 7 Days
-- Metabase panel : Line chart (x=hour, y=log_count, series=service_name)
CREATE OR REPLACE VIEW vw_volume_trends_hourly AS
SELECT
    DATE_TRUNC('hour', timestamp)   AS hour,
    service_name,
    level,
    COUNT(*)                        AS log_count
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '7 days'
GROUP BY DATE_TRUNC('hour', timestamp), service_name, level
ORDER BY hour ASC, service_name, level;



-- VIEW 5: Daily Log Volume per Service — Last 30 Days
-- Metabase panel : Area/Line chart (x=day, y=log_count, series=service_name)
CREATE OR REPLACE VIEW vw_volume_trends_daily AS
SELECT
    DATE_TRUNC('day', timestamp)    AS day,
    service_name,
    level,
    COUNT(*)                        AS log_count
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', timestamp), service_name, level
ORDER BY day ASC, service_name, level;



-- VIEW 6: Log Level Distribution per Service — Last 30 Days
-- Metabase panel : Stacked bar chart (x=service_name, y=count, color=level)
CREATE OR REPLACE VIEW vw_level_distribution AS
SELECT
    service_name,
    level,
    COUNT(*)                                        AS log_count,
    ROUND(
        COUNT(*) * 100.0
        / NULLIF(SUM(COUNT(*)) OVER (PARTITION BY service_name), 0),
        2
    )                                               AS pct_of_service_total
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '30 days'
GROUP BY service_name, level
ORDER BY service_name, level;



-- VIEW 7: Service Activity Heatmap — Hour of Day vs Day of Week
-- Metabase panel : Heatmap (x=hour_of_day, y=day_of_week, color=log_count)
CREATE OR REPLACE VIEW vw_activity_heatmap AS
SELECT
    service_name,
    EXTRACT(DOW  FROM timestamp)::INT   AS day_of_week,   -- 0=Sun … 6=Sat
    EXTRACT(HOUR FROM timestamp)::INT   AS hour_of_day,
    COUNT(*)                            AS log_count
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '30 days'
GROUP BY service_name, day_of_week, hour_of_day
ORDER BY service_name, day_of_week, hour_of_day;


-- Metabase panel : Heatmap (x=hour_of_day, y=day_of_week, color=log_count)

-- VIEW 8: Error Spike Detection — Current 1h vs 7-Day Avg
-- Metabase panel : Table with conditional formatting
--                 (highlight rows where spike_ratio > 2)
-- Why            : Catches services whose error rate just jumped
--                  above their own historical baseline
CREATE OR REPLACE VIEW vw_error_spike_detection AS
WITH baseline AS (
    SELECT
        service_name,
        ROUND(
            AVG(daily_errors), 2
        )                           AS avg_daily_errors_7d
    FROM (
        SELECT
            service_name,
            DATE_TRUNC('day', timestamp) AS day,
            COUNT(*) FILTER (WHERE level = 'ERROR') AS daily_errors
        FROM log_entries
        WHERE timestamp >= NOW() - INTERVAL '7 days'
        GROUP BY service_name, DATE_TRUNC('day', timestamp)
    ) daily
    GROUP BY service_name
),
current_window AS (
    SELECT
        service_name,
        COUNT(*) FILTER (WHERE level = 'ERROR') AS errors_last_1h
    FROM log_entries
    WHERE timestamp >= NOW() - INTERVAL '1 hour'
    GROUP BY service_name
)
SELECT
    b.service_name,
    b.avg_daily_errors_7d,
    COALESCE(c.errors_last_1h, 0)           AS errors_last_1h,
    ROUND(
        COALESCE(c.errors_last_1h, 0) * 24.0
        / NULLIF(b.avg_daily_errors_7d, 0),
        2
    )                                        AS spike_ratio,
    CASE
        WHEN COALESCE(c.errors_last_1h, 0) * 24.0
             / NULLIF(b.avg_daily_errors_7d, 0) > 3  THEN 'CRITICAL'
        WHEN COALESCE(c.errors_last_1h, 0) * 24.0
             / NULLIF(b.avg_daily_errors_7d, 0) > 1.5 THEN 'ELEVATED'
        ELSE 'NORMAL'
    END                                      AS spike_status
FROM baseline b
LEFT JOIN current_window c USING (service_name)
ORDER BY spike_ratio DESC NULLS LAST;



-- VIEW 9: Silent Services (No Logs in Last 10 Minutes)
-- Metabase panel : Table — alert panel showing potentially down services
-- Why            : A service that stops logging may have crashed
CREATE OR REPLACE VIEW vw_silent_services AS
SELECT
    service_name,
    MAX(timestamp)                                          AS last_log_at,
    ROUND(
        EXTRACT(EPOCH FROM (NOW() - MAX(timestamp))) / 60, 1
    )                                                       AS minutes_silent
FROM log_entries
GROUP BY service_name
HAVING MAX(timestamp) < NOW() - INTERVAL '10 minutes'
ORDER BY last_log_at ASC;



-- VIEW 10: Top Noisy Services — Last 24 Hours
-- Metabase panel : Pie or bar chart (x=service_name, y=log_count)
-- Why            : Identifies services driving storage and retention cost
CREATE OR REPLACE VIEW vw_top_noisy_services AS
SELECT
    service_name,
    COUNT(*)                                            AS total_logs,
    ROUND(
        COUNT(*) * 100.0 / NULLIF(SUM(COUNT(*)) OVER (), 0),
        2
    )                                                   AS pct_of_total
FROM log_entries
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY service_name
ORDER BY total_logs DESC;




-- VIEW 11: Service Health Dashboard — Per-Service Summary
-- Metabase panel : Table with status indicator column
-- Why            : Single-glance health overview (Backend Dev C spec)
-- Columns        : service_name, last_log_at, error_rate_24h,
--                  warn_rate_24h, total_logs_24h, status
CREATE OR REPLACE VIEW vw_service_health_dashboard AS
WITH window_24h AS (
    SELECT
        service_name,
        COUNT(*)                                            AS total_logs,
        COUNT(*) FILTER (WHERE level = 'ERROR')             AS error_count,
        COUNT(*) FILTER (WHERE level = 'WARN')              AS warn_count,
        MAX(timestamp)                                      AS last_log_at
    FROM log_entries
    WHERE timestamp >= NOW() - INTERVAL '24 hours'
    GROUP BY service_name
),
window_1h AS (
    SELECT
        service_name,
        COUNT(*) FILTER (WHERE level = 'ERROR')             AS errors_1h
    FROM log_entries
    WHERE timestamp >= NOW() - INTERVAL '1 hour'
    GROUP BY service_name
)
SELECT
    w.service_name,
    w.last_log_at,
    w.total_logs                                            AS total_logs_24h,
    ROUND(w.error_count * 100.0 / NULLIF(w.total_logs, 0), 2) AS error_rate_24h,
    ROUND(w.warn_count  * 100.0 / NULLIF(w.total_logs, 0), 2) AS warn_rate_24h,
    COALESCE(h.errors_1h, 0)                               AS errors_last_1h,
    CASE
        WHEN COALESCE(h.errors_1h, 0) > 10                 THEN 'CRITICAL'
        WHEN w.error_count * 100.0
             / NULLIF(w.total_logs, 0) > 10                THEN 'DEGRADED'
        ELSE 'HEALTHY'
    END                                                     AS status
FROM window_24h w
LEFT JOIN window_1h h USING (service_name)
ORDER BY
    CASE
        WHEN COALESCE(h.errors_1h, 0) > 10                          THEN 0
        WHEN w.error_count * 100.0 / NULLIF(w.total_logs, 0) > 10  THEN 1
        ELSE 2
    END,
    error_rate_24h DESC;



-- VIEW 12: Recent Critical Events — Last 50 Errors
-- Metabase panel : Live feed table (timestamp, service, message)
-- Why            : Real-time error feed for on-call engineers
CREATE OR REPLACE VIEW vw_recent_critical_events AS
SELECT
    timestamp,
    service_name,
    level,
    message
FROM log_entries
WHERE level = 'ERROR'
ORDER BY timestamp DESC
LIMIT 50;



-- VIEW 13: Mean Time Between Errors per Service — Last 7 Days
-- Metabase panel : Bar chart (x=service_name, y=avg_minutes_between_errors)
-- Why            : Reliability metric — higher is better
CREATE OR REPLACE VIEW vw_mtbe_per_service AS
WITH error_logs AS (
    SELECT
        service_name,
        timestamp,
        LAG(timestamp) OVER (
            PARTITION BY service_name ORDER BY timestamp
        )                           AS prev_error_ts
    FROM log_entries
    WHERE
        level     = 'ERROR'
        AND timestamp >= NOW() - INTERVAL '7 days'
)
SELECT
    service_name,
    COUNT(*)                        AS total_errors,
    ROUND(
        AVG(
            EXTRACT(EPOCH FROM (timestamp - prev_error_ts)) / 60
        )::NUMERIC,
        1
    )                               AS avg_minutes_between_errors
FROM error_logs
WHERE prev_error_ts IS NOT NULL
GROUP BY service_name
ORDER BY avg_minutes_between_errors ASC;