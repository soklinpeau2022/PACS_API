-- Standardize operational PACS naming from Queue to Worklist.
-- Keep this as a forward migration instead of rewriting older Flyway files.

ALTER TABLE IF EXISTS pacs_patient_queue RENAME TO pacs_worklists;
ALTER TABLE IF EXISTS pacs_patient_queue_histories RENAME TO pacs_worklist_histories;
ALTER TABLE IF EXISTS pacs_queue_study_links RENAME TO pacs_worklist_study_links;
ALTER TABLE IF EXISTS pacs_queue_results RENAME TO pacs_worklist_results;

DO $$
BEGIN
    IF to_regclass('pacs_worklist_histories') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_worklist_histories' AND column_name = 'queue_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_worklist_histories' AND column_name = 'worklist_id') THEN
        ALTER TABLE pacs_worklist_histories RENAME COLUMN queue_id TO worklist_id;
    END IF;

    IF to_regclass('pacs_worklist_study_links') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_worklist_study_links' AND column_name = 'queue_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_worklist_study_links' AND column_name = 'worklist_id') THEN
        ALTER TABLE pacs_worklist_study_links RENAME COLUMN queue_id TO worklist_id;
    END IF;

    IF to_regclass('pacs_worklist_results') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_worklist_results' AND column_name = 'queue_id')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_worklist_results' AND column_name = 'worklist_id') THEN
        ALTER TABLE pacs_worklist_results RENAME COLUMN queue_id TO worklist_id;
    END IF;

    IF to_regclass('pacs_results') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_results' AND column_name = 'queue_id') THEN
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_results' AND column_name = 'worklist_id')
               AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_results' AND column_name = 'worklist_code') THEN
                ALTER TABLE pacs_results RENAME COLUMN worklist_id TO worklist_code;
            ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_results' AND column_name = 'worklist_id')
               AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_results' AND column_name = 'worklist_code') THEN
                EXECUTE 'UPDATE pacs_results SET worklist_code = COALESCE(worklist_code, worklist_id::text)';
                ALTER TABLE pacs_results DROP COLUMN worklist_id;
            END IF;

            IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'pacs_results' AND column_name = 'worklist_id') THEN
                ALTER TABLE pacs_results RENAME COLUMN queue_id TO worklist_id;
            END IF;
        END IF;

        ALTER TABLE pacs_results ADD COLUMN IF NOT EXISTS worklist_code VARCHAR(120);
    END IF;
END $$;

ALTER INDEX IF EXISTS idx_pacs_patient_queue_hospital_study_id
    RENAME TO idx_pacs_worklists_hospital_study_id;
ALTER INDEX IF EXISTS idx_pacs_queue_study_links_hospital_accession
    RENAME TO idx_pacs_worklist_study_links_hospital_accession;
ALTER INDEX IF EXISTS idx_pacs_queue_study_links_hospital_study_linked_desc
    RENAME TO idx_pacs_worklist_study_links_hospital_study_linked_desc;
ALTER INDEX IF EXISTS ux_pacs_queue_study_links_primary_queue
    RENAME TO ux_pacs_worklist_study_links_primary_worklist;
ALTER INDEX IF EXISTS ux_pacs_queue_study_links_queue_study
    RENAME TO ux_pacs_worklist_study_links_worklist_study;
ALTER INDEX IF EXISTS ux_pacs_results_hospital_modality_queue_active
    RENAME TO ux_pacs_results_hospital_modality_worklist_active;
ALTER INDEX IF EXISTS idx_queue_histories_hospital_queue_created
    RENAME TO idx_worklist_histories_hospital_worklist_created;
ALTER INDEX IF EXISTS idx_queue_histories_patient_created
    RENAME TO idx_worklist_histories_patient_created;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_hospital_status
    RENAME TO idx_pacs_worklists_hospital_status;
ALTER INDEX IF EXISTS idx_queue_hospital_id_desc
    RENAME TO idx_worklist_hospital_id_desc;
ALTER INDEX IF EXISTS ux_pacs_patient_queue_hospital_visit_code
    RENAME TO ux_pacs_worklists_hospital_visit_code;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_visit_code
    RENAME TO idx_pacs_worklists_visit_code;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_hospital_service_status_created
    RENAME TO idx_pacs_worklists_hospital_service_status_created;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_hospital_modality_status_created
    RENAME TO idx_pacs_worklists_hospital_modality_status_created;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_study_uuid
    RENAME TO idx_pacs_worklists_study_uuid;
ALTER INDEX IF EXISTS ux_pacs_queue_results_hospital_queue_active
    RENAME TO ux_pacs_worklist_results_hospital_worklist_active;
ALTER INDEX IF EXISTS idx_pacs_queue_results_hospital_active_created
    RENAME TO idx_pacs_worklist_results_hospital_active_created;
ALTER INDEX IF EXISTS idx_pacs_queue_hospital_status_scheduled_id_desc
    RENAME TO idx_pacs_worklists_hospital_status_scheduled_id_desc;
ALTER INDEX IF EXISTS idx_pacs_queue_hospital_patient_status_id_desc
    RENAME TO idx_pacs_worklists_hospital_patient_status_id_desc;
ALTER INDEX IF EXISTS idx_pacs_queue_visit_code_trgm
    RENAME TO idx_pacs_worklists_visit_code_trgm;
ALTER INDEX IF EXISTS idx_pacs_queue_accession_trgm
    RENAME TO idx_pacs_worklists_accession_trgm;
ALTER INDEX IF EXISTS idx_pacs_queue_study_description_trgm
    RENAME TO idx_pacs_worklists_study_description_trgm;
ALTER INDEX IF EXISTS idx_pacs_queue_hospital_lower_visit_code
    RENAME TO idx_pacs_worklists_hospital_lower_visit_code;
ALTER INDEX IF EXISTS idx_pacs_queue_hospital_lower_accession
    RENAME TO idx_pacs_worklists_hospital_lower_accession;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_operational_hospital_id_desc
    RENAME TO idx_pacs_worklists_operational_hospital_id_desc;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_operational_hospital_schedule
    RENAME TO idx_pacs_worklists_operational_hospital_schedule;
ALTER INDEX IF EXISTS idx_pacs_patient_queue_hospital_status_id_desc
    RENAME TO idx_pacs_worklists_hospital_status_id_desc;

DO $$
BEGIN
    IF to_regclass('pacs_worklists') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_patient_queue_study_id') THEN
        ALTER TABLE pacs_worklists RENAME CONSTRAINT fk_pacs_patient_queue_study_id TO fk_pacs_worklists_study_id;
    END IF;

    IF to_regclass('pacs_worklists') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_patient_queue_status') THEN
        ALTER TABLE pacs_worklists RENAME CONSTRAINT chk_pacs_patient_queue_status TO chk_pacs_worklists_status;
    END IF;

    IF to_regclass('pacs_worklist_study_links') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pacs_queue_study_links_queue_id_fkey') THEN
        ALTER TABLE pacs_worklist_study_links RENAME CONSTRAINT pacs_queue_study_links_queue_id_fkey TO pacs_worklist_study_links_worklist_id_fkey;
    END IF;
END $$;

UPDATE module_types
SET
    code = REPLACE(code, 'PACS_QUEUE', 'PACS_WORKLIST'),
    name = REPLACE(REPLACE(name, 'Queue', 'Worklist'), 'queue', 'worklist'),
    modified = NOW(),
    modified_at = NOW()
WHERE COALESCE(code, '') ILIKE '%queue%'
   OR COALESCE(name, '') ILIKE '%queue%';

UPDATE modules
SET
    code = REPLACE(REPLACE(code, 'pacs-queue', 'pacs-worklist'), 'queue', 'worklist'),
    name = REPLACE(REPLACE(name, 'Queue', 'Worklist'), 'queue', 'worklist'),
    modified = NOW(),
    modified_at = NOW()
WHERE COALESCE(code, '') ILIKE '%queue%'
   OR COALESCE(name, '') ILIKE '%queue%';

UPDATE module_details
SET
    code = REPLACE(REPLACE(code, 'pacs.queue', 'pacs.worklist'), 'queue', 'worklist'),
    name = REPLACE(REPLACE(name, 'Queue', 'Worklist'), 'queue', 'worklist'),
    modified = NOW(),
    modified_at = NOW()
WHERE COALESCE(code, '') ILIKE '%queue%'
   OR COALESCE(name, '') ILIKE '%queue%';

UPDATE endpoint_permissions
SET
    endpoint_pattern = REPLACE(REPLACE(endpoint_pattern, '/queue/', '/worklist/'), 'queue-', 'worklist-'),
    permission_code = REPLACE(REPLACE(permission_code, 'pacs.queue', 'pacs.worklist'), 'queue', 'worklist')
WHERE COALESCE(endpoint_pattern, '') ILIKE '%/queue/%'
   OR COALESCE(endpoint_pattern, '') ILIKE '%queue-%'
   OR COALESCE(permission_code, '') ILIKE '%queue%';
