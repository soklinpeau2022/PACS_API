\set ON_ERROR_STOP on
\pset pager off

-- DEV ONLY.
-- This script resets the public schema and creates the extensions needed before
-- Flyway runs. Start the PACS API after this; Flyway will rebuild the full
-- application schema through the latest migration, including:
--   - pgcrypto / pg_trgm availability before old migrations need them
--   - hospital-safe composite FKs
--   - final CHECK constraints
--   - hot-table index cleanup
--   - partitioned append-only logs
--   - config-driven monthly partition maintenance
--   - unmatched DICOM callback quarantine with hospital-scoped callback logs
--   - fixed-log and policy/audit partition retention rules
--   - pacs_daily_stats dashboard summary support
--
-- Example:
--   psql -d emr_pacs_dev -f tools/sql/dev-reset/final_optimized_dev_schema.sql
--   mvn spring-boot:run
--   psql -d emr_pacs_dev -f tools/sql/final-verification/final_dev_database_verification.sql

DO $$
BEGIN
    IF current_database() !~* '(dev|local|test|perf|bench|migration)' THEN
        RAISE EXCEPTION
            'Refusing DEV reset in database %. Rename/use a disposable DEV database.',
            current_database();
    END IF;
END;
$$;

DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

GRANT ALL ON SCHEMA public TO CURRENT_USER;
GRANT USAGE ON SCHEMA public TO PUBLIC;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

COMMENT ON SCHEMA public IS
    'Reset by final_optimized_dev_schema.sql; application Flyway migrations create the optimized PACS schema.';

SELECT
    current_database() AS database_name,
    'public schema reset; start the API to run all Flyway migrations' AS next_step;
