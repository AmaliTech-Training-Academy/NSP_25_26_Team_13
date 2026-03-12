import os
from dotenv import load_dotenv
from pathlib import Path

# Load .env file
env_path = Path(__file__).resolve().parent.parent / ".env"
load_dotenv(env_path)

# ── Archival Strategy ─────────────────────────────────────────────────────────
# Controls whether expired logs are moved to logs_archive table before deletion.
# Default: disabled (for MVP). Set to "true" in .env to enable.
ARCHIVAL_ENABLED = os.getenv("ARCHIVAL_ENABLED", "false").lower() == "true"

# Fetch environment variables
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": os.getenv("DB_PORT", "5432"),
    "database": os.getenv("DB_NAME", "logstream"),
    "user": os.getenv("DB_USER", "postgres"),
    "password": os.getenv("DB_PASSWORD", "postgres"),
}

DATABASE_URL = (
    f"postgresql://{DB_CONFIG['user']}:{DB_CONFIG['password']}"
    f"@{DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}"
)






# error_rate is informational only — actual generation is driven by LEVEL_WEIGHTS
# and spike overrides in ERROR_SPIKES.
SERVICES = {
    "auth-service":         {"error_rate": 0.05},
    "payment-service":      {"error_rate": 0.15},
    "order-service":        {"error_rate": 0.08},
    "notification-service": {"error_rate": 0.03},
    "api-gateway":          {"error_rate": 0.02},
}


# num_spikes   : how many distinct spike windows to pre-place in the timeline
# duration_minutes : how long each spike window lasts
# error_weight : the ERROR level weight during the spike (baseline is 0.08)
#
# Removed: probability (was a broken per-entry roll)
# Removed: multiplier  (was defined but never used)
ERROR_SPIKES = {
    "payment-service": {
        "num_spikes":        2,
        "duration_minutes":  20,
        "error_weight":      0.65,   # ERROR ~65% of logs during spike
    },
    "order-service": {
        "num_spikes":        1,
        "duration_minutes":  15,
        "error_weight":      0.55,
    },
}

#Service Outage Configuration 
# num_outages      : how many distinct outage windows to pre-place
# duration_minutes : how long each outage lasts (service emits NO logs)
#
# Removed: probability (same broken per-entry roll problem as ERROR_SPIKES)
SERVICE_OUTAGES = {
    "notification-service": {
        "num_outages":       1,
        "duration_minutes":  30,
    },
    "auth-service": {
        "num_outages":       1,
        "duration_minutes":  25,
    },
}

# Log Levels & Baseline Weights
# INFO=60%, DEBUG=20%, WARN=10%, ERROR=8%, TRACE=2%
LEVELS        = ["INFO",  "DEBUG", "WARN", "ERROR", "TRACE"]
LEVEL_WEIGHTS = [ 0.60,   0.20,    0.10,   0.08,    0.02  ]

# Business-Hours Timestamp Weighting 
# 24 values, one per hour 00–23.
# Higher value = more likely a log falls in that hour.
_HOUR_WEIGHTS = [
    0.5, 0.3, 0.2, 0.2, 0.3, 0.5,   # 00–05  (overnight, very quiet)
    0.8, 1.2, 2.0, 2.5, 2.5, 2.5,   # 06–11  (morning ramp-up)
    2.3, 2.5, 2.5, 2.3, 2.0, 1.8,   # 12–17  (core business hours)
    1.5, 1.2, 1.0, 0.9, 0.7, 0.6,   # 18–23  (evening wind-down)
]

# Message Templates
# {n} is replaced at generation time with a random integer.
ERROR_MESSAGES = [
    "Database connection timeout after {n}ms",
    "JWT token expired for user session {n}",
    "Payment gateway unavailable — retry {n} of 3",
    "NullPointerException in OrderProcessor.validate() line {n}",
    "Cache miss threshold exceeded ({n}% misses)",
    "Authentication failed for user admin — invalid credentials",
    "Payment retry attempt {n} of 3 failed",
    "Circuit breaker OPEN after {n} consecutive failures",
    "Failed to acquire DB connection from pool (timeout={n}s)",
    "Order rollback triggered — insufficient inventory for item {n}",
]

INFO_MESSAGES = [
    "Request processed successfully in {n}ms",
    "User authenticated — session token issued",
    "Order #{n} created successfully",
    "Email notification dispatched to user {n}",
    "Metrics snapshot updated",
    "User registration completed successfully for ID {n}",
    "Scheduled job completed: log-cleanup removed {n} expired entries",
    "Service health check passed",
    "Payment of ${n} processed for order #{n}",
    "Batch of {n} records processed in {n}ms",
]

WARN_MESSAGES = [
    "Slow query detected: {n}ms exceeds 500ms threshold",
    "Retry attempt {n} of 3 for failed request",
    "Memory usage at {n}% — approaching limit",
    "Connection pool nearing limit ({n} of {n} used)",
    "Response time degraded: {n}ms average over last 60s",
    "Rate limit approaching for client token {n}",
]

DEBUG_MESSAGES = [
    "Cache lookup for key user:session:{n}",
    "Processing batch of {n} records",
    "Entering OrderProcessor.validate() with orderId={n}",
    "DB query returned {n} rows in {n}ms",
    "Token validation passed for sub=user_{n}",
    "Outbound HTTP POST /payments → 202 Accepted",
]

TRACE_MESSAGES = [
    "Method entry: processPayment(orderId={n})",
    "SQL: SELECT * FROM users WHERE id={n}",
    "HTTP GET /health → 200 OK",
    "Entering filter chain: JwtAuthFilter",
    "Stack frame: OrderService.create() → PaymentClient.charge()",
]

MESSAGE_MAP = {
    "ERROR": ERROR_MESSAGES,
    "INFO":  INFO_MESSAGES,
    "WARN":  WARN_MESSAGES,
    "DEBUG": DEBUG_MESSAGES,
    "TRACE": TRACE_MESSAGES,
}

# Validation Constants
VALID_LEVELS  = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"}
REQUIRED_COLS = {"id", "timestamp", "level", "source", "message","service_name", "created_at"}