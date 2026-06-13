-- Worklists should store the selected route only. The DICOM server is derived
-- from hospital_modality_server_routes.dicom_server_id through dicom_route_id.

UPDATE pacs_worklists q
SET dicom_route_id = r.id
FROM hospital_modality_server_routes r
WHERE q.dicom_route_id IS NULL
  AND q.dicom_server_id IS NOT NULL
  AND r.hospital_id = q.hospital_id
  AND r.modality_id = q.modality_id
  AND r.dicom_server_id = q.dicom_server_id
  AND r.is_active = 1
  AND NOT EXISTS (
      SELECT 1
      FROM hospital_modality_server_routes other_route
      WHERE other_route.hospital_id = q.hospital_id
        AND other_route.modality_id = q.modality_id
        AND other_route.dicom_server_id = q.dicom_server_id
        AND other_route.is_active = 1
        AND other_route.id <> r.id
  );

DROP INDEX IF EXISTS idx_pacs_worklists_hospital_dicom_server;
DROP INDEX IF EXISTS idx_pacs_worklists_dicom_server_status;
DROP INDEX IF EXISTS idx_pacs_queue_hospital_dicom_server_status;

ALTER TABLE pacs_worklists
    DROP COLUMN IF EXISTS dicom_server_id;

COMMENT ON COLUMN pacs_worklists.dicom_route_id IS 'Selected DICOM machine route relation. DICOM server is derived from hospital_modality_server_routes.';
