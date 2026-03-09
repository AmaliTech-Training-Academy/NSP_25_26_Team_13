/*
DESCRIPTION: 
    Initializes a scalable database environment for a logging service. 
    Implements partitioned time-series storage, case-insensitive user 
    authentication, and advanced indexing for high-performance searching.

CORE ARCHITECTURE:
    1. EXTENSIONS: Enables 'pg_trgm' for fuzzy text search and 'citext' 
       for case-insensitive email handling.
    2. USER MANAGEMENT: Dedicated 'users' table with RBAC (Role-Based 
       Access Control) and security-focused data types.
    3. LOG PARTITIONING: The 'log_entries' table uses Range Partitioning 
       by timestamp to handle high-volume data ingestion and cleanup.
    4. RETENTION: A 'retention_policies' framework to manage log 
       lifecycles based on service name and severity level.

INDEXING STRATEGY:
    - B-Tree: Standard lookups for user auth and service filtering.
    - BRIN (Block Range Index): Optimized for massive time-series scans 
      and volume trend analysis with minimal disk overhead.
    - GIN (Generalized Inverted Index): Enables high-speed trigram 
      keyword searches within raw log messages.
*/

-- Extension for Keyword/Trigram search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. Users Table (For Authentication)
CREATE EXTENSION IF NOT EXISTS citext;  -- Use the 'citext' extension for case-insensitive email storage

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email CITEXT UNIQUE NOT NULL, -- Prevents duplicate emails regardless of casing
    name VARCHAR(100) NOT NULL,
    password TEXT NOT NULL, -- Store Bcrypt/Argon2 hashes here
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    active BOOLEAN DEFAULT true,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Partitioned Log Entries
-- Note: PK must include the partition key (timestamp)
CREATE TABLE log_entries (
    id UUID DEFAULT gen_random_uuid(),
    timestamp TIMESTAMPTZ NOT NULL,
    level VARCHAR(10) NOT NULL CHECK (level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR')),
    source VARCHAR(100),
    message TEXT NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (timestamp, id) 
) PARTITION BY RANGE (timestamp);

-- 3. Retention Policies (As defined by Backend)
CREATE TABLE retention_policies (
    id SERIAL PRIMARY KEY,
    service_name VARCHAR(100), -- nullable = global policy
    name VARCHAR(100),
    retention_days INTEGER DEFAULT 30,
    log_level VARCHAR(10) CHECK (log_level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR')),
    active BOOLEAN DEFAULT true,
    UNIQUE (service_name, log_level) -- prevent duplicate policies
);



-- Indexing for authentication
-- When a user logs in, the Java backend will query 'WHERE email = ?'
CREATE INDEX idx_users_email ON users (email);

-- Initial Partition (Required so the app doesn't crash on the first INSERT)
CREATE TABLE log_entries_default PARTITION OF log_entries DEFAULT;

-- For Backend Dev B: Search by service, level, and time
CREATE INDEX idx_logs_search_lookup 
ON log_entries (service_name, level, timestamp DESC);

-- For Backend Dev C: Volume trends and Error rates (Aggregations)
CREATE INDEX idx_logs_volume_brin 
ON log_entries USING BRIN (timestamp); -- BRIN is extremely efficient for large time-series scans.

-- For Keyword search in the message field
CREATE INDEX idx_logs_msg_search 
ON log_entries USING GIN (message gin_trgm_ops);

-- Metrics Aggregation Tables
CREATE TABLE log_metrics_hourly (
    service_name VARCHAR(100) NOT NULL,
    hour_timestamp TIMESTAMPTZ NOT NULL,
    total_count BIGINT DEFAULT 0,
    error_count BIGINT DEFAULT 0,
    PRIMARY KEY (service_name, hour_timestamp)
);

CREATE TABLE log_metrics_daily (
    service_name VARCHAR(100) NOT NULL,
    day_timestamp TIMESTAMPTZ NOT NULL,
    total_count BIGINT DEFAULT 0,
    error_count BIGINT DEFAULT 0,
    PRIMARY KEY (service_name, day_timestamp)
);

-- Index for fast analytics queries
CREATE INDEX idx_metrics_hourly_ts ON log_metrics_hourly (hour_timestamp DESC);
CREATE INDEX idx_metrics_daily_ts ON log_metrics_daily (day_timestamp DESC);