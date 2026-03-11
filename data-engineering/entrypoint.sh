#!/bin/sh

# Export environment variables for cron
# This ensures that variables like DB_HOST, DB_NAME, etc. are available to cron jobs
printenv | grep -v "no_proxy" >> /etc/environment

# Apply crontab
crontab /app/crontab

# Start cron daemon in the foreground
echo "Starting cron daemon (LogStream ETL)..."
exec cron -f
