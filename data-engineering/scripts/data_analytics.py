"""
data_analytics.py · LogStream Observability Platform
=====================================================
Validation + analytics functions for log data produced by data_generator.py.

Field contract (matches backend log_entries table and generator output):
    id, timestamp, level, source, message, service_name, created_at

All analytics functions accept a *cleaned* DataFrame — the first return
value of validate(). Never pass raw data directly to an analytics function.

Design notes
------------
* Timestamps are stored as timezone-naive UTC throughout. validate() strips
  timezone info on ingestion so every comparison is naive-vs-naive.
* _window() is the single source of truth for time-windowing; all analytics
  functions call it rather than filtering inline.
* Per-service baseline error rates from config are used by the generator
  so that payment-service genuinely has a higher baseline than api-gateway.
  validate() does not need to know about this — it only checks data shape.
"""

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

import pandas as pd

from config.config import REQUIRED_COLS, SERVICES, VALID_LEVELS
from scripts.utils.logger import get_logger
from scripts.validation import ValidationReport,validate 

logger = get_logger("data_analytics")




# SHARED HELPERS
def _load_df(path: str) -> pd.DataFrame:
    """Load a generated log file (JSON or CSV) into a raw DataFrame."""
    if path.endswith(".json"):
        return pd.read_json(path)
    return pd.read_csv(path)


def _window(df: pd.DataFrame, days: int = 0, hours: int = 0) -> pd.DataFrame:
    """
    Return rows within the last N days + hours from now (naive UTC).

    df["timestamp"] must already be naive UTC — guaranteed by validate().
    cutoff is also naive UTC so the comparison is always type-consistent.
    """
    cutoff = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(
        days=days, hours=hours
    )
    return df[df["timestamp"] >= cutoff]


def _service_level_weights(service_name: str) -> list[float]:
    """
    Build per-service level weights that honour the error_rate in SERVICES config.

    This is used by the generator (via data_generator.py) so that
    payment-service (error_rate=0.15) genuinely has a higher baseline
    error rate than api-gateway (error_rate=0.02).

    The configured error_rate takes ERROR's slot; remaining probability
    is distributed across the other four levels using their relative
    proportions from the flat LEVEL_WEIGHTS baseline.

    LEVELS order: INFO, DEBUG, WARN, ERROR, TRACE
    """
    from config.config import LEVELS, LEVEL_WEIGHTS

    svc_cfg     = SERVICES.get(service_name, {})
    error_rate  = svc_cfg.get("error_rate", 0.08)   # fallback to flat baseline
    error_idx   = LEVELS.index("ERROR")

    # Sum of all non-ERROR baseline weights
    base_sum    = sum(w for i, w in enumerate(LEVEL_WEIGHTS) if i != error_idx)
    remaining   = 1.0 - error_rate

    weights = []
    for i, w in enumerate(LEVEL_WEIGHTS):
        if i == error_idx:
            weights.append(error_rate)
        else:
            weights.append(remaining * (w / base_sum))
    return weights




# All accept a cleaned DataFrame (output of validate()).
def error_rate_24h(df: pd.DataFrame, service_name: str = None) -> pd.DataFrame:
    """
    Error rate per service for the last 24 hours.

    Returns
    -------
    DataFrame: service_name, total_logs, error_count, error_rate_percent
    """
    data = _window(df, hours=24)
    if service_name:
        data = data[data["service_name"] == service_name]
    if data.empty:
        return pd.DataFrame(
            columns=["service_name", "total_logs", "error_count", "error_rate_percent"]
        )
    return (
        data.groupby("service_name")
        .agg(
            total_logs  =("level", "count"),
            error_count =("level", lambda x: (x == "ERROR").sum()),
        )
        .assign(
            error_rate_percent=lambda d: (
                d["error_count"] / d["total_logs"] * 100
            ).round(2)
        )
        .reset_index()
        .sort_values("error_rate_percent", ascending=False)
    )


def common_errors_top_n(
    df: pd.DataFrame,
    top_n: int = 10,
    service_name: str = None,
    days_back: int = 30,
) -> pd.DataFrame:
    """
    Top N most frequent ERROR messages, optionally filtered to one service.

    Returns
    -------
    DataFrame: service_name, message, occurrences
    """
    data = _window(df, days=days_back)
    data = data[data["level"] == "ERROR"]
    if service_name:
        data = data[data["service_name"] == service_name]
    if data.empty:
        return pd.DataFrame(columns=["service_name", "message", "occurrences"])
    return (
        data.groupby(["service_name", "message"])
        .size()
        .reset_index(name="occurrences")
        .sort_values("occurrences", ascending=False)
        .head(top_n)
        .reset_index(drop=True)
    )


def volume_trends_hourly(
    df: pd.DataFrame,
    service_name: str = None,
    days_back: int = 7,
) -> pd.DataFrame:
    """
    Log volume per service broken down by hour × level.

    Returns
    -------
    DataFrame: hour, service_name, level, log_count
    """
    data = _window(df, days=days_back)
    if service_name:
        data = data[data["service_name"] == service_name]
    if data.empty:
        return pd.DataFrame(columns=["hour", "service_name", "level", "log_count"])
    data = data.copy()
    data["hour"] = data["timestamp"].dt.floor("h")
    return (
        data.groupby(["hour", "service_name", "level"])
        .size()
        .reset_index(name="log_count")
        .sort_values(["hour", "service_name", "level"])
        .reset_index(drop=True)
    )


def volume_trends_daily(
    df: pd.DataFrame,
    service_name: str = None,
    days_back: int = 30,
) -> pd.DataFrame:
    """
    Log volume per service broken down by day × level.

    Returns
    -------
    DataFrame: day, service_name, level, log_count
    """
    data = _window(df, days=days_back)
    if service_name:
        data = data[data["service_name"] == service_name]
    if data.empty:
        return pd.DataFrame(columns=["day", "service_name", "level", "log_count"])
    data = data.copy()
    data["day"] = data["timestamp"].dt.floor("D")
    return (
        data.groupby(["day", "service_name", "level"])
        .size()
        .reset_index(name="log_count")
        .sort_values(["day", "service_name", "level"])
        .reset_index(drop=True)
    )


def warn_rate_24h(df: pd.DataFrame) -> pd.DataFrame:
    """
    WARN rate per service for the last 24 hours.
    WARNs are leading indicators — a rising warn rate often precedes errors.

    Returns
    -------
    DataFrame: service_name, total_logs, warn_count, warn_rate_percent
    """
    data = _window(df, hours=24)
    if data.empty:
        return pd.DataFrame(
            columns=["service_name", "total_logs", "warn_count", "warn_rate_percent"]
        )
    return (
        data.groupby("service_name")
        .agg(
            total_logs =("level", "count"),
            warn_count =("level", lambda x: (x == "WARN").sum()),
        )
        .assign(
            warn_rate_percent=lambda d: (
                d["warn_count"] / d["total_logs"] * 100
            ).round(2)
        )
        .reset_index()
        .sort_values("warn_rate_percent", ascending=False)
    )


def level_distribution(df: pd.DataFrame, days_back: int = 30) -> pd.DataFrame:
    """
    Log level breakdown per service as counts and % of that service's total.

    Returns
    -------
    DataFrame: service_name, level, log_count, pct_of_service_total
    """
    data = _window(df, days=days_back)
    if data.empty:
        return pd.DataFrame(
            columns=["service_name", "level", "log_count", "pct_of_service_total"]
        )
    counts = (
        data.groupby(["service_name", "level"])
        .size()
        .reset_index(name="log_count")
    )
    totals = counts.groupby("service_name")["log_count"].transform("sum")
    counts["pct_of_service_total"] = (counts["log_count"] / totals * 100).round(2)
    return counts.sort_values(["service_name", "level"]).reset_index(drop=True)


def activity_heatmap(df: pd.DataFrame, days_back: int = 30) -> pd.DataFrame:
    """
    Log volume by hour-of-day × day-of-week per service.
    Reveals business-hours patterns and anomalous off-hours activity.

    Returns
    -------
    DataFrame: service_name, day_of_week (0=Mon), hour_of_day, log_count
    """
    data = _window(df, days=days_back).copy()
    if data.empty:
        return pd.DataFrame(
            columns=["service_name", "day_of_week", "hour_of_day", "log_count"]
        )
    data["day_of_week"] = data["timestamp"].dt.dayofweek
    data["hour_of_day"] = data["timestamp"].dt.hour
    return (
        data.groupby(["service_name", "day_of_week", "hour_of_day"])
        .size()
        .reset_index(name="log_count")
        .sort_values(["service_name", "day_of_week", "hour_of_day"])
        .reset_index(drop=True)
    )


def error_spike_detection(df: pd.DataFrame) -> pd.DataFrame:
    """
    Compare each service's errors in the last 1 hour against its
    7-day daily average to surface anomalous spikes.

    spike_ratio = (errors_last_1h * 24) / avg_daily_errors_7d
    Multiplying by 24 converts the 1-hour count to a daily-equivalent
    so it is directly comparable to the 7-day average.

    spike_status thresholds:
        > 3.0  → CRITICAL
        > 1.5  → ELEVATED
        else   → NORMAL

    Returns
    -------
    DataFrame: service_name, avg_daily_errors_7d, errors_last_1h,
               spike_ratio, spike_status
    """
    # 7-day daily baseline — one row per service
    week_data      = _window(df, days=7).copy()
    week_data["day"] = week_data["timestamp"].dt.floor("D")
    baseline = (
        week_data[week_data["level"] == "ERROR"]
        .groupby(["service_name", "day"])
        .size()
        .reset_index(name="daily_errors")
        .groupby("service_name")["daily_errors"]
        .mean()
        .round(2)
        .reset_index(name="avg_daily_errors_7d")
    )

    # Last 1 hour error count
    current = (
        _window(df, hours=1)
        .query("level == 'ERROR'")
        .groupby("service_name")
        .size()
        .reset_index(name="errors_last_1h")
    )

    result = (
        baseline
        .merge(current, on="service_name", how="left")
        .fillna({"errors_last_1h": 0})
    )
    result["errors_last_1h"] = result["errors_last_1h"].astype(int)
    result["spike_ratio"] = (
        result["errors_last_1h"] * 24.0
        / result["avg_daily_errors_7d"].replace(0, float("nan"))
    ).round(2)

    def _status(row) -> str:
        if row["spike_ratio"] > 3.0:   return "CRITICAL"
        if row["spike_ratio"] > 1.5:   return "ELEVATED"
        return "NORMAL"

    result["spike_status"] = result.apply(_status, axis=1)
    return result.sort_values("spike_ratio", ascending=False).reset_index(drop=True)


def silent_services(df: pd.DataFrame, silent_minutes: int = 10) -> pd.DataFrame:
    """
    Services that have not emitted any log in the last `silent_minutes`.
    A service that stops logging may have crashed or been scaled down.

    Returns
    -------
    DataFrame: service_name, last_log_at, minutes_silent
    """
    now_naive = datetime.now(timezone.utc).replace(tzinfo=None)
    cutoff    = now_naive - timedelta(minutes=silent_minutes)

    last_seen = (
        df.groupby("service_name")["timestamp"]
        .max()
        .reset_index(name="last_log_at")
    )
    silent = last_seen[last_seen["last_log_at"] < cutoff].copy()
    silent["minutes_silent"] = (
        (now_naive - silent["last_log_at"]).dt.total_seconds() / 60
    ).round(1)
    return silent.sort_values("last_log_at").reset_index(drop=True)


def top_noisy_services(df: pd.DataFrame) -> pd.DataFrame:
    """
    Services ranked by log volume in the last 24 hours.
    Useful for identifying services driving storage and retention cost.

    Returns
    -------
    DataFrame: service_name, total_logs, pct_of_total
    """
    data = _window(df, hours=24)
    if data.empty:
        return pd.DataFrame(columns=["service_name", "total_logs", "pct_of_total"])
    counts = (
        data.groupby("service_name")
        .size()
        .reset_index(name="total_logs")
    )
    counts["pct_of_total"] = (
        counts["total_logs"] / counts["total_logs"].sum() * 100
    ).round(2)
    return counts.sort_values("total_logs", ascending=False).reset_index(drop=True)


def mean_time_between_errors(
    df: pd.DataFrame, days_back: int = 7
) -> pd.DataFrame:
    """
    Average minutes between consecutive ERROR logs per service.
    Higher value = more stable service (errors are rare and spread out).

    Returns
    -------
    DataFrame: service_name, total_errors, avg_minutes_between_errors
    """
    data   = _window(df, days=days_back)
    errors = data[data["level"] == "ERROR"].copy().sort_values("timestamp")

    if errors.empty:
        return pd.DataFrame(
            columns=["service_name", "total_errors", "avg_minutes_between_errors"]
        )

    errors["prev_ts"]     = errors.groupby("service_name")["timestamp"].shift(1)
    errors["gap_minutes"] = (
        (errors["timestamp"] - errors["prev_ts"]).dt.total_seconds() / 60
    )

    return (
        errors.dropna(subset=["gap_minutes"])
        .groupby("service_name")
        .agg(
            total_errors              =("level", "count"),
            avg_minutes_between_errors=("gap_minutes", lambda x: round(x.mean(), 1)),
        )
        .reset_index()
        .sort_values("avg_minutes_between_errors")
    )


def recent_critical_events(
    df: pd.DataFrame, limit: int = 50
) -> pd.DataFrame:
    """
    Most recent ERROR logs across all services, newest first.
    Designed for a live feed / alert panel.

    Returns
    -------
    DataFrame: timestamp, service_name, level, message
    """
    return (
        df[df["level"] == "ERROR"][
            ["timestamp", "service_name", "level", "message"]
        ]
        .sort_values("timestamp", ascending=False)
        .head(limit)
        .reset_index(drop=True)
    )



# CLI / SMOKE TEST

if __name__ == "__main__":
    import sys

    path = sys.argv[1] if len(sys.argv) > 1 else "./data/logs_20260309_105403.json"
    logger.info(f"Loading: {path}")

    raw_df = _load_df(path)
    logger.info(f"Loaded {len(raw_df):,} raw records")

    df, report = validate(raw_df)
    if not report.passed():
        logger.warning(f"{report.invalid_rows:,} invalid rows dropped before analytics.")

    logger.info("\nError Rate (24h)\n%s",
                error_rate_24h(df).to_string(index=False))

    logger.info("\n-- Top 10 Common Errors (30d) --\n%s",
                common_errors_top_n(df).to_string(index=False))

    logger.info("\n-- Hourly Volume Trends (7d, first 20 rows) --\n%s",
                volume_trends_hourly(df).head(20).to_string(index=False))

    logger.info("\n-- Daily Volume Trends (30d, first 20 rows) --\n%s",
                volume_trends_daily(df).head(20).to_string(index=False))

    logger.info("\n-- Warn Rate (24h) --\n%s",
                warn_rate_24h(df).to_string(index=False))

    logger.info("\n-- Level Distribution (30d) --\n%s",
                level_distribution(df).to_string(index=False))

    logger.info("\n-- Activity Heatmap (30d, first 20 rows) --\n%s",
                activity_heatmap(df).head(20).to_string(index=False))

    logger.info("\n-- Error Spike Detection --\n%s",
                error_spike_detection(df).to_string(index=False))

    logger.info("\n-- Silent Services (last 10 min) --\n%s",
                silent_services(df).to_string(index=False))

    logger.info("\n-- Top Noisy Services (24h) --\n%s",
                top_noisy_services(df).to_string(index=False))

    logger.info("\n-- Mean Time Between Errors (7d) --\n%s",
                mean_time_between_errors(df).to_string(index=False))

    logger.info("\n-- Recent Critical Events (last 25) --\n%s",
                recent_critical_events(df, limit=25).to_string(index=False))