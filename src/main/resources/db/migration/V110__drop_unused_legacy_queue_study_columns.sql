-- Drop columns left behind by older queue/reporting/viewer designs.
-- Current workflow keeps queue statuses independent and stores study result state in pacs_studies.

ALTER TABLE pacs_patient_queue
    DROP COLUMN IF EXISTS translated_by,
    DROP COLUMN IF EXISTS translated_at,
    DROP COLUMN IF EXISTS reported_at,
    DROP COLUMN IF EXISTS completed_at,
    DROP COLUMN IF EXISTS sent_to_pacs_at;

ALTER TABLE pacs_studies
    DROP COLUMN IF EXISTS assigned_to;

DROP INDEX IF EXISTS idx_pacs_patient_queue_status_accession_active;
