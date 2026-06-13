-- Ensure the primary DICOM modality catalog exists on fresh imports and existing deployments.
-- Safety rule: insert missing rows only. Do not update, reactivate, rename, or otherwise
-- modify existing modality data because production hospitals may already depend on it.
DO $$
DECLARE
    seed RECORD;
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
        IF NOT EXISTS (
            SELECT 1
              FROM modalities m
             WHERE LOWER(BTRIM(COALESCE(m.abbr, ''))) = LOWER(seed.abbr)
                OR LOWER(BTRIM(m.name)) = LOWER(seed.name)
        ) THEN
            INSERT INTO modalities (name, abbr, is_active, created_by, modified_by, created_at, modified_at)
            VALUES (seed.name, seed.abbr, 1, 1, 1, NOW(), NOW());
        END IF;
    END LOOP;
END $$;
