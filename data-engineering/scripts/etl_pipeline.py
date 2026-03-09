"""ETL Pipeline for LogStream - Log Analytics & Aggregation"""
import pandas as pd
from sqlalchemy import create_engine, text
from config import DATABASE_URL

engine = create_engine(DATABASE_URL)

def extract_logs():
    query = text("""
        SELECT id, timestamp, level, source, message, service_name, created_at
        FROM log_entries
        ORDER BY timestamp DESC
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)

def transform_level_distribution(logs_df):
    """Count logs by level and service."""
    if logs_df.empty:
        return pd.DataFrame()
    return logs_df.groupby(["level", "service_name"]).size().reset_index(name="count")

def transform_hourly_volume(logs_df):
    """Aggregate log volume by hour."""
    if logs_df.empty:
        return pd.DataFrame()
    logs_df["hour"] = pd.to_datetime(logs_df["timestamp"]).dt.floor("H")
    return logs_df.groupby(["hour", "level"]).size().reset_index(name="count")

def transform_error_patterns(logs_df):
    """Identify recurring error messages."""
    if logs_df.empty:
        return pd.DataFrame()
    errors = logs_df[logs_df["level"] == "ERROR"].copy()
    if errors.empty:
        return pd.DataFrame()
    patterns = errors.groupby(["source", "message"]).size().reset_index(name="occurrences")
    return patterns.sort_values("occurrences", ascending=False).head(20)

def load_analytics(df, table_name):
    df.to_sql(table_name, engine, if_exists="replace", index=False)
    print(f"Loaded {len(df)} rows into {table_name}")

def run_pipeline():
    print("Starting LogStream ETL pipeline...")
    logs_df = extract_logs()
    print(f"Extracted {len(logs_df)} log entries")

    level_dist = transform_level_distribution(logs_df)
    if not level_dist.empty:
        load_analytics(level_dist, "analytics_level_distribution")

    hourly = transform_hourly_volume(logs_df)
    if not hourly.empty:
        load_analytics(hourly, "analytics_hourly_volume")

    errors = transform_error_patterns(logs_df)
    if not errors.empty:
        load_analytics(errors, "analytics_error_patterns")

    # TODO: Add anomaly detection (spike in error rate)
    # TODO: Add service dependency mapping
    # TODO: Add log retention compliance check
    print("ETL pipeline complete!")

if __name__ == "__main__":
    run_pipeline()
