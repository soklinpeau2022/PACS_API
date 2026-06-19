\set ON_ERROR_STOP on
\pset pager off

-- V185 must not run until these hospital-scoped normalized keys are clean.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM patients
        GROUP BY hospital_id, LOWER(patient_uid)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate hospital-scoped patient_uid values exist (case-insensitive).';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pacs_worklists
        WHERE visit_code IS NOT NULL
          AND BTRIM(visit_code) <> ''
        GROUP BY hospital_id, LOWER(visit_code)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate hospital-scoped visit_code values exist (case-insensitive).';
    END IF;
END
$$;

-- Inspect any values that would collide globally but are valid after V185.
SELECT LOWER(patient_uid) AS normalized_patient_uid,
       count(DISTINCT hospital_id) AS hospital_count,
       count(*) AS row_count
FROM patients
GROUP BY LOWER(patient_uid)
HAVING count(DISTINCT hospital_id) > 1
ORDER BY row_count DESC, normalized_patient_uid;

SELECT LOWER(visit_code) AS normalized_visit_code,
       count(DISTINCT hospital_id) AS hospital_count,
       count(*) AS row_count
FROM pacs_worklists
WHERE visit_code IS NOT NULL
  AND BTRIM(visit_code) <> ''
GROUP BY LOWER(visit_code)
HAVING count(DISTINCT hospital_id) > 1
ORDER BY row_count DESC, normalized_visit_code;

-- DB paths must remain storage-relative, never public URLs.
SELECT id, result_id, image_path
FROM pacs_result_images
WHERE image_path ~* '^[a-z][a-z0-9+.-]*://';

-- Viewer state safety checks.
SELECT id, hospital_id, payload_size_bytes, payload_sha256
FROM pacs_viewer_states
WHERE payload_size_bytes < 0
   OR payload_size_bytes > 10485760
   OR (payload_sha256 IS NOT NULL AND payload_sha256 !~ '^[0-9a-f]{64}$');

-- Constraints left NOT VALID should be reviewed and validated in a low-traffic
-- window after existing data has been repaired.
SELECT conrelid::regclass AS table_name,
       conname,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
  AND NOT convalidated
ORDER BY conrelid::regclass::text, conname;

-- Invalid/unfinished concurrent indexes must be removed or rebuilt first.
SELECT tbl.relname AS table_name,
       idx.relname AS index_name,
       pi.indisvalid,
       pi.indisready,
       pi.indislive
FROM pg_index pi
JOIN pg_class idx ON idx.oid = pi.indexrelid
JOIN pg_class tbl ON tbl.oid = pi.indrelid
JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
WHERE ns.nspname = 'public'
  AND (NOT pi.indisvalid OR NOT pi.indisready OR NOT pi.indislive)
ORDER BY tbl.relname, idx.relname;
