UPDATE pacs_studies
SET instance_count = GREATEST(
        COALESCE(instance_count, 0),
        COALESCE(
            ARRAY_LENGTH(
                ARRAY_REMOVE(
                    STRING_TO_ARRAY(REGEXP_REPLACE(COALESCE(dicom_server_series_id, ''), '[[:space:]]+', '', 'g'), ','),
                    ''
                ),
                1
            ),
            0
        )
    )
WHERE is_active = 1
  AND dicom_server_study_id IS NOT NULL;
