#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE admin;
    CREATE DATABASE notification;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname=admin <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS postgis;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname=notification <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS postgis;
EOSQL
