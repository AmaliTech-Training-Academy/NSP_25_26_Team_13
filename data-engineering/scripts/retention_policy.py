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
    Enforces retention policies with a Disk-First safety approach:
    1. SELECT expired rows into memory.
    2. Write to CSV (confirming disk backup).
    3. Perform DB Transaction (INSERT to archive -> DELETE from live).
    """
    policies = get_retention_policies()
    if policies.empty:
        logger.warning("No active retention policies found. Using default 30 days for all logs.")
        policies = pd.DataFrame([{"service_name": None, "log_level": None, "retention_days": 30}])

    archive_dir = Path(__file__).parent.parent / "archives"
    archive_dir.mkdir(parents=True, exist_ok=True)

    for _, policy in policies.iterrows():
        service = policy.get("service_name")
        level = policy.get("log_level")
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
        
        # Unique timestamp for filename to prevent overwrites
        file_ts = datetime.utcnow().strftime('%Y%m%d_%H%M%S')
        policy_label = f"{service or 'ALL'}_{level or 'ALL'}_{file_ts}"

        try:
            # 1. SELECT DATA FIRST
            # We pull the data into a DataFrame before starting a transaction
            select_query = text(f"SELECT * FROM log_entries WHERE {where_clause}")
            with engine.connect() as conn:
                expired_data = pd.read_sql(select_query, conn, params=params)

            if expired_data.empty:
                logger.info(f"No expired logs found for policy [{policy_label}]")
                continue

            # 2. WRITE TO CSV (OUTSIDE TRANSACTION)
            # If this fails, the DB remains untouched.
            if ARCHIVAL_ENABLED:
                archive_file = archive_dir / f"archived_{policy_label}.csv"
                expired_data.to_csv(archive_file, index=False)
                logger.info(f"Disk backup successful: {archive_file}")

            # 3. DATABASE MUTATION (INSIDE TRANSACTION)
            # Now that the CSV is safe, we move/delete the rows in the DB.
            with engine.begin() as transaction_conn:
                if ARCHIVAL_ENABLED:
                    archive_query = text(
                        f"INSERT INTO logs_archive (id, timestamp, level, source, message, service_name, created_at, archived_at) "
                        f"SELECT id, timestamp, level, source, message, service_name, created_at, NOW() "
                        f"FROM log_entries WHERE {where_clause}"
                    )
                    res_arch = transaction_conn.execute(archive_query, params)
                    logger.info(f"Archived {res_arch.rowcount} rows to logs_archive table.")

                delete_query = text(f"DELETE FROM log_entries WHERE {where_clause}")
                res_del = transaction_conn.execute(delete_query, params)
                logger.info(f"Deleted {res_del.rowcount} rows from live table.")

        except Exception as e:
            logger.error(f"Failed to enforce policy [{policy_label}]: {e}")


if __name__ == "__main__":
    enforce_retention()