"""
LogStream ETL Pipeline - Production Grade
Focus: Incremental Processing, Health Metrics, and Partition Management
"""
import re
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


def extract_incremental_logs(minutes=15):
    """Extracts only recent logs to minimize DB load."""
    query = text("""
        SELECT id, timestamp, level, message, service_name, source
        FROM log_entries
        WHERE timestamp >= NOW() - INTERVAL '1 minute' * :mins
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"mins": minutes})


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


def load_data(df, table_name, method="append"):
    """Loads data using append to preserve history."""
    if df.empty:
        return
    df.to_sql(table_name, engine, if_exists=method, index=False)
    logger.info(f"Successfully loaded {len(df)} rows to {table_name}")


def run_pipeline():
    try:
        # 1. Maintenance Task — create today's AND tomorrow's partitions
        manage_partitions()

        # 2. Extract last 60 minutes for volume trends
        logs_df = extract_incremental_logs(minutes=60)
        if logs_df.empty:
            logger.info("No new logs found. Skipping transformation.")
            return

        # 3. Extract last 24 hours specifically for the health dashboard (MVP requirement)
        health_logs_df = extract_incremental_logs(minutes=1440)
        
        # 4. Transform & Load Health Dashboard
        health_df = transform_health_metrics(health_logs_df)
        
        # Use TRUNCATE + INSERT inside a transaction to avoid table-not-found errors
        # that would occur with method="replace" (which DROPs the table briefly)
        with engine.begin() as conn:
            try:
                conn.execute(text("TRUNCATE TABLE service_health_dashboard;"))
            except Exception:
                pass  # Table may not exist on first run; to_sql will create it
        load_data(health_df, "service_health_dashboard", method="append")

        # 5. Load Volume Trends (Idempotent Append)
        hourly_vol = logs_df.groupby([pd.to_datetime(logs_df["timestamp"]).dt.floor("h"), "level", "service_name"]).size().reset_index(name="count")
        
        if not hourly_vol.empty:
            # 5a. Identify the unique hours in this batch
            unique_hours = hourly_vol["timestamp"].dt.strftime('%Y-%m-%d %H:%M:%S').unique().tolist()
            
            # 5b. Delete existing records for these hours to prevent duplicates (Idempotency)
            hours_str = ", ".join([f"'{h}'" for h in unique_hours])
            delete_query = text(f"DELETE FROM analytics_volume_trends WHERE timestamp IN ({hours_str})")
            
            with engine.begin() as conn:
                try:
                    conn.execute(delete_query)
                except Exception as e:
                    logger.warning(f"Could not cleanly delete existing volume trends (table might be new): {e}")

            # 5c. Append the fresh data
            load_data(hourly_vol, "analytics_volume_trends", method="append")

        logger.info("Pipeline run successful.")
        
    except Exception as e:
        logger.error(f"Pipeline failed: {str(e)}")

if __name__ == "__main__":
    run_pipeline()