-- Keep received study launch data normalized.
-- The study stores only the source DICOM server FK; API responses build viewer,
-- DICOM server UI, and DICOMweb URLs dynamically from hospital_dicom_servers.

ALTER TABLE pacs_studies
    ADD COLUMN IF NOT EXISTS dicom_server_id BIGINT;

UPDATE pacs_studies study
SET dicom_server_id = route.dicom_server_id,
    modified = NOW()
FROM pacs_worklist_study_links link
INNER JOIN pacs_worklists worklist
    ON worklist.id = link.worklist_id
   AND worklist.hospital_id = link.hospital_id
INNER JOIN hospital_modality_server_routes route
    ON route.id = worklist.dicom_route_id
   AND route.hospital_id = worklist.hospital_id
WHERE study.id = link.study_id
  AND study.hospital_id = link.hospital_id
  AND link.is_primary = 1
  AND study.dicom_server_id IS NULL;

WITH single_active_server AS (
    SELECT
        hospital_id,
        MIN(id) AS dicom_server_id,
        COUNT(*) AS server_count
    FROM hospital_dicom_servers
    WHERE is_active = 1
    GROUP BY hospital_id
)
UPDATE pacs_studies study
SET dicom_server_id = server.dicom_server_id,
    modified = NOW()
FROM single_active_server server
WHERE study.hospital_id = server.hospital_id
  AND study.dicom_server_id IS NULL
  AND server.server_count = 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'pacs_studies'
          AND constraint_name = 'fk_pacs_studies_dicom_server_hospital'
    ) THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT fk_pacs_studies_dicom_server_hospital
                FOREIGN KEY (dicom_server_id, hospital_id)
                REFERENCES hospital_dicom_servers(id, hospital_id)
                NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_dicom_server
    ON pacs_studies (hospital_id, dicom_server_id, id DESC)
    WHERE dicom_server_id IS NOT NULL
      AND is_active = 1;

COMMENT ON COLUMN pacs_studies.dicom_server_id IS 'Source DICOM server relation for received studies. Viewer URLs are generated dynamically from this server.';
