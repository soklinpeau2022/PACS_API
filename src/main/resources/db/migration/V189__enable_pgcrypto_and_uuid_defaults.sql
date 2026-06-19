-- V189: Adopt pgcrypto / gen_random_uuid() for public identifier defaults.
--
-- gen_random_uuid() is part of PostgreSQL core since 13 (this stack runs PG18);
-- CREATE EXTENSION pgcrypto is kept for portability and is a no-op where it is
-- already installed. Only column DEFAULTS change here -- existing public_id
-- values, the columns themselves, and their unique indexes are untouched, so
-- there is zero application impact. New rows simply get a cryptographically
-- random UUID instead of md5(random()::text || clock_timestamp()::text)::uuid.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    item RECORD;
BEGIN
    FOR item IN
        SELECT *
        FROM (VALUES
            ('patients', 'public_id'),
            ('pacs_studies', 'public_id'),
            ('pacs_worklists', 'public_id'),
            ('pacs_results', 'result_public_id'),
            ('pacs_result_images', 'image_public_id'),
            ('pacs_viewer_states', 'public_id'),
            ('pacs_result_versions', 'public_id'),
            ('study_retention_policies', 'public_id'),
            ('study_retention_delete_requests', 'public_id')
        ) AS t(table_name, column_name)
    LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = item.table_name
              AND column_name = item.column_name
        ) THEN
            EXECUTE format(
                'ALTER TABLE public.%I ALTER COLUMN %I SET DEFAULT gen_random_uuid()',
                item.table_name,
                item.column_name
            );
        END IF;
    END LOOP;
END;
$$;
