-- Active Worklist rows are only the operational rows that have not yet produced
-- received study/image data. Once image metadata exists, the row is read from
-- the Studies archive instead of the active Worklist list.

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_active_hospital_status_schedule_id_desc
    ON pacs_worklists (hospital_id, status, scheduled_date, id DESC)
    WHERE status IN (1, 2, 3, 4)
      AND study_id IS NULL
      AND COALESCE(image_received_at, received_at) IS NULL
      AND NULLIF(BTRIM(COALESCE(study_instance_uid, '')), '') IS NULL
      AND NULLIF(BTRIM(COALESCE(dicom_server_study_id, '')), '') IS NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_worklists_active_patient_modality
    ON pacs_worklists (hospital_id, patient_id, modality_id, status, id DESC)
    WHERE status IN (1, 2, 4)
      AND study_id IS NULL
      AND COALESCE(image_received_at, received_at) IS NULL
      AND NULLIF(BTRIM(COALESCE(study_instance_uid, '')), '') IS NULL
      AND NULLIF(BTRIM(COALESCE(dicom_server_study_id, '')), '') IS NULL;
