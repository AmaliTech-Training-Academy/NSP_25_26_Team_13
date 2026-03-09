# Using the specific version requested in the project spec
FROM python:3.11-slim

# Optimization: Prevent Python from writing .pyc files and buffer logs
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONPATH=/app

WORKDIR /app

# Install system dependencies for Postgres and healthchecks
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq-dev \
    gcc \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Security: Create a non-privileged user
RUN groupadd -r de_user && useradd -r -g de_user de_user

# Install Python dependencies
COPY requirements.txt .
RUN pip install --upgrade pip && \
    pip install -r requirements.txt

# Copy source code with correct ownership
COPY --chown=de_user:de_user . .

# Ensure the data and archives directories exist and are owned by de_user
RUN mkdir -p /app/data /app/archives && chown -R de_user:de_user /app/data /app/archives

# Switch to non-root user
USER de_user

# Healthcheck: Verify psycopg2 (DB driver) is importable
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD python -c "import psycopg2; print('Check passed')" || exit 1

# Default: run the ETL pipeline once.
# docker-compose overrides this with a periodic loop.
CMD ["python", "scripts/etl_pipeline.py"]