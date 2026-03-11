#!/bin/bash
set -e

# This script ensures the 'metabase' database exists.
# It is mounted to /docker-entrypoint-initdb.d/ in the postgres container.

echo "Checking if database 'metabase' exists..."
if psql -U "$POSTGRES_USER" -lqt | cut -d \| -f 1 | grep -qw metabase; then
    echo "Database 'metabase' already exists."
else
    echo "Creating database 'metabase'..."
    psql -U "$POSTGRES_USER" -c "CREATE DATABASE metabase"
    echo "Database 'metabase' created successfully."
fi
