"""
LogStream Retention Enforcer - Per-Service Archival
Enforces configurable retention policies per service and log level.
When archival is enabled, moves expired rows to logs_archive table
and exports them to CSV before permanently deleting them.
"""
import pandas as pd
from sqlalchemy import create_engine, text
from datetime import datetime, timedelta
from pathlib import Path
from config.config import DATABASE_URL, ARCHIVAL_ENABLED
from utils.logger import get_logger

logger = get_logger("RetentionEnforcer")
engine = create_engine(DATABASE_URL)


def get_retention_policies():
    """Fetches active retention rules set by the admin, including per-service policies."""
    query = "SELECT service_name, log_level, retention_days FROM retention_policies WHERE active = true"
    return pd.read_sql(query, engine)


def enforce_retention():
    """
    Enforces retention policies by:
    1. Building per-service WHERE clauses from the retention_policies table.
    2. If ARCHIVAL_ENABLED:
       a. Inserting expired rows into logs_archive table.
       b. Exporting them to a dated CSV backup on disk.
    3. Deleting expired rows from the live log_entries table.

    If no policies exist, falls back to a global 30-day default for all services/levels.
    """
    policies = get_retention_policies()
    if policies.empty:
        logger.warning("No active retention policies found. Using default 30 days for all logs.")
        policies = pd.DataFrame([{"service_name": None, "log_level": None, "retention_days": 30}])

    archive_dir = Path(__file__).parent.parent / "archives"
    archive_dir.mkdir(parents=True, exist_ok=True)

    with engine.begin() as conn:
        for _, policy in policies.iterrows():
            service = policy.get("service_name")  # None = global
            level = policy.get("log_level")        # None = all levels
            retention_days = int(policy["retention_days"])
            cutoff_date = datetime.utcnow() - timedelta(days=retention_days)

            # Build the dynamic WHERE clause
            conditions = ["timestamp < :cutoff"]
            params = {"cutoff": cutoff_date}

            if pd.notna(service):
                conditions.append("service_name = :service")
                params["service"] = service

            if pd.notna(level):
                conditions.append("level = :level")
                params["level"] = level

            where_clause = " AND ".join(conditions)
            policy_label = f"{service or 'ALL_SERVICES'}_{level or 'ALL_LEVELS'}_{cutoff_date.strftime('%Y%m%d')}"

            try:
                if ARCHIVAL_ENABLED:
                    # Step 1: Archive expired rows to logs_archive table
                    archive_query = text(
                        f"INSERT INTO logs_archive (id, timestamp, level, source, message, service_name, created_at, archived_at) "
                        f"SELECT id, timestamp, level, source, message, service_name, created_at, NOW() "
                        f"FROM log_entries WHERE {where_clause}"
                    )
                    result = conn.execute(archive_query, params)
                    logger.info(f"Archived {result.rowcount} rows to logs_archive for policy [{policy_label}]")

                    # Step 2: Export to CSV on disk as backup
                    select_query = text(f"SELECT * FROM log_entries WHERE {where_clause}")
                    expired_data = pd.read_sql(select_query, conn, params=params)

                    if not expired_data.empty:
                        archive_file = archive_dir / f"archived_{policy_label}.csv"
                        expired_data.to_csv(archive_file, index=False)
                        logger.info(f"CSV backup: {len(expired_data)} rows to {archive_file}")

                # Step 3: Delete expired rows from live table
                delete_query = text(f"DELETE FROM log_entries WHERE {where_clause}")
                result = conn.execute(delete_query, params)
                logger.info(f"Deleted {result.rowcount} expired rows for policy [{policy_label}]")

            except Exception as e:
                logger.error(f"Failed to enforce policy [{policy_label}]: {e}")


if __name__ == "__main__":
    enforce_retention()