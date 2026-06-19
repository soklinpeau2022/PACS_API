-- V190: Validate every still-pending CHECK and FOREIGN KEY constraint.
--
-- V170-V188 added the hospital-safe composite FKs and data-domain CHECKs as
-- NOT VALID: new writes and parent deletes were enforced immediately, while
-- existing rows were left to be validated online. This migration flips each
-- remaining NOT VALID constraint to validated, codifying the manual
-- tools/sql/deep-database-refactor/finalize_constraints.sql step into the
-- Flyway history so QA/prod validate automatically on deploy.
--
-- VALIDATE CONSTRAINT takes a SHARE UPDATE EXCLUSIVE lock and scans the table
-- once; normal reads and writes continue. It is idempotent: environments where
-- finalize_constraints.sql already ran (e.g. local DEV) have zero pending
-- constraints and this loop simply does nothing.

DO $$
DECLARE
    item RECORD;
BEGIN
    FOR item IN
        SELECT rel.relname AS table_name,
               con.conname AS constraint_name
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace ns ON ns.oid = rel.relnamespace
        WHERE ns.nspname = 'public'
          AND con.contype IN ('c', 'f')
          AND NOT con.convalidated
        ORDER BY rel.relname, con.conname
    LOOP
        RAISE NOTICE 'V190 validating % on %', item.constraint_name, item.table_name;
        EXECUTE format(
            'ALTER TABLE public.%I VALIDATE CONSTRAINT %I',
            item.table_name,
            item.constraint_name
        );
    END LOOP;
END;
$$;
