-- Remove remaining duplicated PACS reference text.
-- Worklists keep relation IDs and operational state; display values are derived
-- from patients, modalities, studies, DICOM machines, and DICOM server callback logs.

ALTER TABLE pacs_worklists
    ADD COLUMN IF NOT EXISTS dicom_route_id BIGINT;

UPDATE pacs_worklists q
SET dicom_route_id = r.id
FROM hospital_modality_server_routes r
INNER JOIN hospital_dicom_machines machine
    ON machine.id = r.machine_id
   AND machine.hospital_id = r.hospital_id
   AND machine.modality_id = r.modality_id
   AND machine.is_active = 1
WHERE q.dicom_route_id IS NULL
  AND q.hospital_id = r.hospital_id
  AND q.modality_id = r.modality_id
  AND r.is_active = 1
  AND (q.dicom_server_id IS NULL OR q.dicom_server_id = r.dicom_server_id)
  AND NULLIF(BTRIM(q.machine_ae_title), '') IS NOT NULL
  AND LOWER(machine.machine_ae_title) = LOWER(BTRIM(q.machine_ae_title));

WITH single_route AS (
    SELECT
        q.id AS worklist_id,
        q.hospital_id,
        MIN(r.id) AS route_id,
        COUNT(*) AS route_count
    FROM pacs_worklists q
    INNER JOIN hospital_modality_server_routes r
        ON r.hospital_id = q.hospital_id
       AND r.modality_id = q.modality_id
       AND r.is_active = 1
       AND (q.dicom_server_id IS NULL OR q.dicom_server_id = r.dicom_server_id)
    WHERE q.dicom_route_id IS NULL
    GROUP BY q.id, q.hospital_id
)
UPDATE pacs_worklists q
SET
    dicom_route_id = single_route.route_id,
    dicom_server_id = COALESCE(q.dicom_server_id, r.dicom_server_id)
FROM single_route
INNER JOIN hospital_modality_server_routes r ON r.id = single_route.route_id
WHERE q.id = single_route.worklist_id
  AND q.hospital_id = single_route.hospital_id
  AND single_route.route_count = 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'pacs_worklists'
          AND constraint_name = 'fk_pacs_worklists_dicom_route'
    ) THEN
        ALTER TABLE pacs_worklists
            ADD CONSTRAINT fk_pacs_worklists_dicom_route
                FOREIGN KEY (dicom_route_id)
                REFERENCES hospital_modality_server_routes(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_dicom_route
    ON pacs_worklists (hospital_id, dicom_route_id, status, id DESC)
    WHERE dicom_route_id IS NOT NULL;

DROP INDEX IF EXISTS ux_pacs_worklists_hospital_accession;
DROP INDEX IF EXISTS idx_pacs_worklists_status_accession;
DROP INDEX IF EXISTS idx_pacs_worklists_accession_trgm;
DROP INDEX IF EXISTS idx_pacs_worklists_hospital_lower_accession;
DROP INDEX IF EXISTS idx_pacs_result_images_hospital_modality;

ALTER TABLE pacs_worklists
    DROP COLUMN IF EXISTS accession_number,
    DROP COLUMN IF EXISTS modality_code,
    DROP COLUMN IF EXISTS machine_ae_title;

ALTER TABLE pacs_studies
    DROP COLUMN IF EXISTS viewer_url;

ALTER TABLE pacs_result_images
    DROP COLUMN IF EXISTS hospital_id,
    DROP COLUMN IF EXISTS modality_id;

ALTER TABLE system_activities
    DROP COLUMN IF EXISTS act;

COMMENT ON COLUMN pacs_worklists.dicom_route_id IS 'Selected DICOM machine route relation. Machine AE title is derived from hospital_dicom_machines through this route.';
