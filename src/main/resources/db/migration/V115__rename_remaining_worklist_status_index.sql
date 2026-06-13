-- Keep the narrow hospital/status lookup index, but finish Queue -> Worklist naming.

DO $$
BEGIN
    IF to_regclass('public.idx_pacs_queue_hospital_status') IS NOT NULL
       AND to_regclass('public.idx_pacs_worklists_hospital_status_lookup') IS NULL THEN
        ALTER INDEX idx_pacs_queue_hospital_status
            RENAME TO idx_pacs_worklists_hospital_status_lookup;
    END IF;
END $$;
