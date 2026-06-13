-- Seed the standard DICOM modality catalog used by worklist and routing setup.
-- Hospital modality assignments remain hospital-specific and are not auto-created here.
DO $$
DECLARE
    seed RECORD;
    active_id BIGINT;
    inactive_id BIGINT;
    name_match_id BIGINT;
BEGIN
    FOR seed IN
        SELECT *
        FROM (VALUES
            ('CR', 'Computed Radiography'),
            ('CT', 'Computed Tomography'),
            ('DR', 'Digital Radiography'),
            ('DX', 'Diagnostic X-Ray'),
            ('MG', 'Mammography'),
            ('MR', 'Magnetic Resonance Imaging'),
            ('OT', 'Other'),
            ('PT', 'Positron Emission Tomography'),
            ('US', 'Ultrasound'),
            ('XA', 'X-Ray Angiography'),
            ('XC', 'External-Camera Photography')
        ) AS v(abbr, name)
    LOOP
        active_id := NULL;
        inactive_id := NULL;
        name_match_id := NULL;

        SELECT m.id
          INTO active_id
          FROM modalities AS m
         WHERE m.is_active = 1
           AND LOWER(BTRIM(COALESCE(m.abbr, ''))) = LOWER(seed.abbr)
         ORDER BY m.id
         LIMIT 1;

        IF active_id IS NOT NULL THEN
            UPDATE modalities AS m
               SET abbr = seed.abbr,
                   modified_at = NOW()
             WHERE m.id = active_id
               AND m.abbr IS DISTINCT FROM seed.abbr;
            CONTINUE;
        END IF;

        SELECT m.id
          INTO inactive_id
          FROM modalities AS m
         WHERE m.is_active <> 1
           AND LOWER(BTRIM(COALESCE(m.abbr, ''))) = LOWER(seed.abbr)
         ORDER BY CASE WHEN m.is_active = 2 THEN 0 ELSE 1 END, m.id
         LIMIT 1;

        IF inactive_id IS NOT NULL THEN
            UPDATE modalities AS m
               SET name = seed.name,
                   abbr = seed.abbr,
                   is_active = 1,
                   modified_by = COALESCE(m.modified_by, 1),
                   modified_at = NOW()
             WHERE m.id = inactive_id;
            CONTINUE;
        END IF;

        SELECT m.id
          INTO name_match_id
          FROM modalities AS m
         WHERE m.is_active = 1
           AND LOWER(BTRIM(m.name)) = LOWER(seed.name)
         ORDER BY m.id
         LIMIT 1;

        IF name_match_id IS NOT NULL THEN
            UPDATE modalities AS m
               SET abbr = seed.abbr,
                   modified_at = NOW()
             WHERE m.id = name_match_id
               AND (m.abbr IS NULL OR BTRIM(m.abbr) = '' OR LOWER(BTRIM(m.abbr)) <> LOWER(seed.abbr))
               AND NOT EXISTS (
                   SELECT 1
                     FROM modalities AS existing_modality
                    WHERE existing_modality.is_active = 1
                      AND LOWER(BTRIM(COALESCE(existing_modality.abbr, ''))) = LOWER(seed.abbr)
                      AND existing_modality.id <> m.id
               );
            CONTINUE;
        END IF;

        INSERT INTO modalities (name, abbr, is_active, created_by, modified_by, created_at, modified_at)
        VALUES (seed.name, seed.abbr, 1, 1, 1, NOW(), NOW());
    END LOOP;
END $$;
