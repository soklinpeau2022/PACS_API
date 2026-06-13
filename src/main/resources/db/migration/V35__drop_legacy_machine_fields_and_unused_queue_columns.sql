-- Cleanup migration:
-- 1) Remove direct-machine columns from hospitals table (API-to-API architecture with DicomServer).
-- 2) Remove legacy unused queue columns from pacs_patient_queue.
-- 3) Remove temporary local test table when present.

ALTER TABLE hospitals
    DROP COLUMN IF EXISTS ae_title,
    DROP COLUMN IF EXISTS dicom_host,
    DROP COLUMN IF EXISTS dicom_port;

ALTER TABLE pacs_patient_queue
    DROP COLUMN IF EXISTS study_id,
    DROP COLUMN IF EXISTS assigned_to;

DROP TABLE IF EXISTS zz_test_table;
