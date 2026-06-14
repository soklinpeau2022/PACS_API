ALTER TABLE pacs_studies
    ADD COLUMN IF NOT EXISTS instance_count INTEGER;

UPDATE pacs_studies
SET instance_count = CASE
        WHEN dicom_server_study_id IS NULL THEN 0
        WHEN dicom_server_series_id IS NULL OR BTRIM(dicom_server_series_id) = '' THEN 1
        ELSE GREATEST(
                1,
                ARRAY_LENGTH(
                    ARRAY_REMOVE(
                        STRING_TO_ARRAY(REGEXP_REPLACE(dicom_server_series_id, '\s+', '', 'g'), ','),
                        ''
                    ),
                    1
                )
        )
    END
WHERE instance_count IS NULL
  AND dicom_server_study_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_instance_count_active
    ON pacs_studies (hospital_id, instance_count)
    WHERE is_active = 1;
