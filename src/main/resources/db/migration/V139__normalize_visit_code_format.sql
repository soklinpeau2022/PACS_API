CREATE OR REPLACE FUNCTION pg_temp.visit_code_hospital_token(raw_value TEXT, fallback_value TEXT)
RETURNS TEXT AS $$
DECLARE
    normalized TEXT;
    compact TEXT;
    token TEXT;
BEGIN
    normalized := UPPER(TRIM(COALESCE(raw_value, '')));
    IF normalized <> '' THEN
        FOREACH token IN ARRAY regexp_split_to_array(normalized, '[^A-Z0-9]+') LOOP
            IF token ~ '^[A-Z0-9]{2,10}$'
               AND token NOT IN ('HOSPITAL', 'HOSP', 'CLINIC', 'CENTER', 'CENTRE') THEN
                RETURN token;
            END IF;
        END LOOP;
    END IF;

    compact := regexp_replace(normalized, '[^A-Z0-9]', '', 'g');
    IF compact = '' THEN
        compact := UPPER(regexp_replace(COALESCE(fallback_value, 'HOSP'), '[^A-Z0-9]', '', 'g'));
    END IF;
    IF compact = '' THEN
        compact := 'HOSP';
    END IF;
    RETURN LEFT(compact, 10);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.visit_code_modality_token(raw_abbr TEXT, raw_name TEXT)
RETURNS TEXT AS $$
DECLARE
    cleaned_abbr TEXT;
    normalized_name TEXT;
BEGIN
    cleaned_abbr := UPPER(regexp_replace(COALESCE(raw_abbr, ''), '[^A-Z0-9]', '', 'g'));
    IF cleaned_abbr ~ '^[A-Z0-9]{2,8}$' THEN
        RETURN cleaned_abbr;
    END IF;

    normalized_name := UPPER(TRIM(COALESCE(raw_name, '')));
    IF normalized_name LIKE '%CT%' THEN RETURN 'CT'; END IF;
    IF normalized_name LIKE '%MRI%' OR normalized_name = 'MR' THEN RETURN 'MRI'; END IF;
    IF normalized_name LIKE '%XRAY%' OR normalized_name = 'X-RAY' OR normalized_name = 'XR' THEN RETURN 'CR'; END IF;
    IF normalized_name LIKE '%DX%' THEN RETURN 'DX'; END IF;
    IF normalized_name LIKE '%US%' OR normalized_name LIKE '%ULTRASOUND%' OR normalized_name LIKE '%ECHO%' THEN RETURN 'US'; END IF;
    IF normalized_name LIKE '%MG%' THEN RETURN 'MG'; END IF;
    IF normalized_name LIKE '%NM%' THEN RETURN 'NM'; END IF;
    IF normalized_name LIKE '%PET%' OR normalized_name = 'PT' THEN RETURN 'PT'; END IF;
    RETURN 'OT';
END;
$$ LANGUAGE plpgsql;

CREATE TEMP TABLE tmp_visit_code_normalization ON COMMIT DROP AS
SELECT
    w.id AS worklist_id,
    w.visit_code AS old_visit_code,
    pg_temp.visit_code_modality_token(m.abbr, m.name)
        || '-' || pg_temp.visit_code_hospital_token(COALESCE(h.abbr, h.code, h.name), 'H' || COALESCE(w.hospital_id::TEXT, ''))
        || '-' || SUBSTRING(w.visit_code FROM LENGTH(w.visit_code) - 9 FOR 6)
        || '-' || RIGHT(w.visit_code, 4) AS new_visit_code
FROM pacs_worklists w
LEFT JOIN modalities m ON m.id = w.modality_id
LEFT JOIN hospitals h ON h.id = w.hospital_id
WHERE w.visit_code IS NOT NULL
  AND w.visit_code <> ''
  AND w.visit_code ~ '^[A-Z0-9]+[0-9]{10}$'
  AND w.visit_code !~ '^[A-Z0-9]+-[A-Z0-9]+-[0-9]{6}-[0-9]{4}$';

UPDATE pacs_studies study
SET accession_number = map.new_visit_code
FROM pacs_worklist_study_links link
INNER JOIN tmp_visit_code_normalization map ON map.worklist_id = link.worklist_id
WHERE study.id = link.study_id
  AND study.accession_number = map.old_visit_code;

UPDATE dicom_server_callback_log log
SET accession_number = map.new_visit_code
FROM tmp_visit_code_normalization map
WHERE log.accession_number = map.old_visit_code;

UPDATE pacs_worklists worklist
SET visit_code = map.new_visit_code
FROM tmp_visit_code_normalization map
WHERE worklist.id = map.worklist_id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_worklists_visit_code_global
    ON pacs_worklists (LOWER(visit_code))
    WHERE visit_code IS NOT NULL AND visit_code <> '';
