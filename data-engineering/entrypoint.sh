#!/bin/sh

# Export environment variables for cron
printenv | grep -v "no_proxy" >> /etc/environment

# Apply crontab
crontab /app/crontab

# Start cron daemon
echo "Starting cron daemon..."
cron -f
