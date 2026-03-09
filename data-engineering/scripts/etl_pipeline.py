"""
LogStream ETL Pipeline - Production Grade
Focus: Incremental Processing, Health Metrics, Metrics Aggregation, and Partition Management
"""
import re
import argparse
import pandas as pd
from sqlalchemy import create_engine, text
from datetime import datetime, timedelta
from utils.logger import get_logger

logger = get_logger("ETLPipeline")

from config.config import DATABASE_URL
engine = create_engine(DATABASE_URL)


def _create_partition(conn, target_date):
    """Creates a single partition for a given date, with name sanitization."""
    suffix = target_date.strftime('%Y_%m_%d')
    table_name = f"log_entries_y{suffix}"
    
    # Sanitize partition name to prevent SQL injection
    if not re.match(r'^log_entries_y\d{4}_\d{2}_\d{2}$', table_name):
        raise ValueError(f"Invalid partition name: {table_name}")
    
    start_date = target_date.strftime('%Y-%m-%d')
    end_date = (target_date + timedelta(days=1)).strftime('%Y-%m-%d')
    
    query = text(f"""
        CREATE TABLE IF NOT EXISTS {table_name} 
        PARTITION OF log_entries 
        FOR VALUES FROM ('{start_date}') TO ('{end_date}');
    """)
    conn.execute(query)
    return table_name


def manage_partitions():
    """Ensures partitions exist for both today and tomorrow."""
    now = datetime.utcnow()
    today = now.replace(hour=0, minute=0, second=0, microsecond=0)
    tomorrow = today + timedelta(days=1)
    
    with engine.begin() as conn:
        today_part = _create_partition(conn, today)
        logger.info(f"Verified partition: {today_part}")
        
        tomorrow_part = _create_partition(conn, tomorrow)
        logger.info(f"Verified partition: {tomorrow_part}")


def extract_logs(start_time, end_time):
    """Extracts logs within a specific time range."""
    query = text("""
        SELECT id, timestamp, level, message, service_name, source
        FROM log_entries
        WHERE timestamp >= :start AND timestamp < :end
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"start": start_time, "end": end_time})


def extract_incremental_logs(minutes=15):
    """Old method for lookback-based extraction."""
    end = datetime.utcnow()
    start = end - timedelta(minutes=minutes)
    return extract_logs(start, end)


def transform_health_metrics(logs_df):
    """Calculates error rates and last log per service for the Health Dashboard."""
    if logs_df.empty:
        return pd.DataFrame()

    # Calculate metrics
    health = logs_df.groupby("service_name").agg(
        last_log=("timestamp", "max"),
        total_logs=("id", "count"),
        error_logs=("level", lambda x: (x == 'ERROR').sum())
    ).reset_index()
    
    health["error_rate"] = (health["error_logs"] / health["total_logs"]) * 100
    # Status indicator logic
    health["status"] = health["error_rate"].apply(lambda x: "CRITICAL" if x > 15 else "STABLE")
    
    return health


def aggregate_metrics(logs_df, period_start, period_type="hour"):
    """Aggregates counts per service for metrics tables."""
    if logs_df.empty:
        return pd.DataFrame()
    
    metrics = logs_df.groupby("service_name").agg(
        total_count=("id", "count"),
        error_count=("level", lambda x: (x == 'ERROR').sum())
    ).reset_index()
    
    if period_type == "hour":
        metrics["hour_timestamp"] = period_start
    else:
        metrics["day_timestamp"] = period_start
        
    return metrics


def load_data(df, table_name, method="append"):
    """Loads data using append to preserve history."""
    if df.empty:
        return
    df.to_sql(table_name, engine, if_exists=method, index=False)
    # Logging row count as required
    logger.info(f"Successfully loaded {len(df)} rows to {table_name}")


def run_aggregation(mode="hourly"):
    """Runs incremental metrics aggregation for the previous period."""
    start_time = datetime.utcnow()
    logger.info(f"Starting {mode} aggregation job at {start_time}")
    
    try:
        now = datetime.utcnow()
        if mode == "hourly":
            # Previous full hour (e.g. if run at 1:05, get 0:00-1:00)
            period_start = (now - timedelta(hours=1)).replace(minute=0, second=0, microsecond=0)
            period_end = period_start + timedelta(hours=1)
            target_table = "log_metrics_hourly"
            ts_col = "hour_timestamp"
        else:
            # Previous full day (e.g. if run Monday 1AM, get all of Sunday)
            period_start = (now - timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
            period_end = period_start + timedelta(days=1)
            target_table = "log_metrics_daily"
            ts_col = "day_timestamp"
            
        logger.info(f"Extracting logs for range: {period_start} to {period_end}")
        logs_df = extract_logs(period_start, period_end)
        
        if logs_df.empty:
            logger.info(f"No logs found for the previous {mode} period.")
        else:
            metrics_df = aggregate_metrics(logs_df, period_start, "hour" if mode == "hourly" else "day")
            
            # Idempotency: Delete existing for this period_start before inserting
            with engine.begin() as conn:
                conn.execute(text(f"DELETE FROM {target_table} WHERE {ts_col} = :ts"), {"ts": period_start})
            
            load_data(metrics_df, target_table, method="append")
            
        logger.info(f"Finished {mode} aggregation job at {datetime.utcnow()}. Duration: {datetime.utcnow() - start_time}")
        
    except Exception as e:
        logger.error(f"{mode.capitalize()} aggregation job failed: {str(e)}")


def run_standard_pipeline():
    """Maintains health dashboard and partitions (standard run)."""
    start_time = datetime.utcnow()
    logger.info(f"Starting standard pipeline run at {start_time}")
    
    try:
        # 1. Maintenance Task — create today's AND tomorrow's partitions
        manage_partitions()

        # 2. Extract last 24 hours specifically for the health dashboard (MVP requirement)
        health_logs_df = extract_incremental_logs(minutes=1440)
        
        if health_logs_df.empty:
            logger.info("No logs found for health dashboard.")
        else:
            health_df = transform_health_metrics(health_logs_df)
            with engine.begin() as conn:
                try:
                    conn.execute(text("TRUNCATE TABLE service_health_dashboard;"))
                except Exception:
                    pass  # Table may not exist on first run
            load_data(health_df, "service_health_dashboard", method="append")

        logger.info(f"Finished standard pipeline run at {datetime.utcnow()}. Duration: {datetime.utcnow() - start_time}")
        
    except Exception as e:
        logger.error(f"Standard pipeline run failed: {str(e)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="LogStream ETL Pipeline")
    parser.add_argument("--mode", choices=["hourly", "daily", "standard"], default="standard", 
                        help="Select run mode (default: standard)")
    args = parser.parse_args()
    
    if args.mode == "hourly":
        run_aggregation(mode="hourly")
    elif args.mode == "daily":
        run_aggregation(mode="daily")
    else:
        run_standard_pipeline()