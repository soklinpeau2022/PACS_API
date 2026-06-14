ALTER TABLE pacs_studies
    ADD COLUMN IF NOT EXISTS modality_id BIGINT;

UPDATE pacs_studies s
SET modality_id = m.id
FROM modalities m
WHERE s.modality_id IS NULL
  AND s.modality IS NOT NULL
  AND m.is_active = 1
  AND (
      UPPER(BTRIM(s.modality)) = UPPER(BTRIM(COALESCE(m.abbr, '')))
      OR UPPER(BTRIM(s.modality)) = UPPER(BTRIM(COALESCE(m.name, '')))
  )
  AND EXISTS (
      SELECT 1
      FROM hospital_modalities hm
      WHERE hm.hospital_id = s.hospital_id
        AND hm.modality_id = m.id
        AND hm.is_active = 1
  );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_pacs_studies_hospital_modality'
    ) THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT fk_pacs_studies_hospital_modality
                FOREIGN KEY (hospital_id, modality_id)
                REFERENCES hospital_modalities(hospital_id, modality_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacs_studies_hospital_modality_active
    ON pacs_studies (hospital_id, modality_id, id DESC)
    WHERE is_active = 1;
