#!/bin/bash
# =============================================================
# Ensure Metabase Database Exists
# Team 13 | AmaliTech Training Academy
#
# This script runs on EVERY postgres startup (not just fresh init).
# It creates the metabase database if it does not already exist.
# Metabase uses this separate database to store its application data
# (dashboards, queries, users, etc.)
# =============================================================

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Ensuring metabase database exists..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE metabase'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'metabase')\gexec
EOSQL

echo "✓ Metabase database check complete."
echo ""
