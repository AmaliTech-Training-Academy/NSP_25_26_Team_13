# Using the specific version requested in the project spec
FROM python:3.11-slim

# Optimization: Prevent Python from writing .pyc files and buffer logs
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONPATH=/app

WORKDIR /app

# Install system dependencies for Postgres, healthchecks, and cron
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq-dev \
    gcc \
    curl \
    cron \
    procps \
    && rm -rf /var/lib/apt/lists/*

# Security: Create a non-privileged user and group
RUN groupadd -r de_user && useradd -r -g de_user de_user

# Install Python dependencies
COPY requirements.txt .
RUN pip install --upgrade pip && \
    pip install -r requirements.txt

# Copy source code
COPY . .

# Prepare directories and fix line endings for entrypoint scripts (CRLF to LF)
RUN mkdir -p /app/data /app/archives /app/logs && \
    chmod +x /app/entrypoint.sh && \
    sed -i 's/\r$//' /app/entrypoint.sh && \
    sed -i 's/\r$//' /app/crontab

# Note: We do NOT switch to USER de_user here because the cron daemon 
# needs root privileges to start. The entrypoint.sh handles initialization.

# Healthcheck: Verify psycopg2 (DB driver) is importable
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD python -c "import psycopg2; print('Check passed')" || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]