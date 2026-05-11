#!/bin/bash
set -e

# Служебная БД Metabase и её владелец
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER metabase_app WITH PASSWORD '${METABASE_DB_PASSWORD}';
    CREATE DATABASE metabase_app OWNER metabase_app;

    CREATE USER metabase_reader WITH PASSWORD '${METABASE_READER_PASSWORD}';
    GRANT CONNECT ON DATABASE athletica TO metabase_reader;
EOSQL

# Права read-only для основной БД
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "athletica" <<-EOSQL
    GRANT USAGE ON SCHEMA public TO metabase_reader;
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO metabase_reader;
    GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO metabase_reader;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO metabase_reader;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO metabase_reader;
EOSQL
