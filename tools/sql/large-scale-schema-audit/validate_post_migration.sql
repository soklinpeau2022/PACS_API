\set ON_ERROR_STOP on
\pset pager off

DO $$
DECLARE
    required_index text;
BEGIN
    FOREACH required_index IN ARRAY ARRAY[
        'ux_patients_hospital_patient_uid_lower',
        'ux_pacs_worklists_hospital_visit_code_lower',
        'idx_pacs_worklists_lower_visit_code'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_index pi
            JOIN pg_class idx ON idx.oid = pi.indexrelid
            JOIN pg_namespace ns ON ns.oid = idx.relnamespace
            WHERE ns.nspname = 'public'
              AND idx.relname = required_index
              AND pi.indisvalid
              AND pi.indisready
        ) THEN
            RAISE EXCEPTION 'Required valid index is missing: %', required_index;
        END IF;
    END LOOP;

    IF to_regclass('public.ux_patients_patient_uid_global_lower') IS NOT NULL THEN
        RAISE EXCEPTION 'Global patient UID uniqueness still exists.';
    END IF;

    IF to_regclass('public.ux_pacs_worklists_visit_code_global') IS NOT NULL THEN
        RAISE EXCEPTION 'Global worklist visit-code uniqueness still exists.';
    END IF;
END
$$;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'ux_patients_hospital_patient_uid_lower',
      'ux_pacs_worklists_hospital_visit_code_lower',
      'idx_pacs_worklists_lower_visit_code'
  )
ORDER BY indexname;

-- Must return no rows.
SELECT hospital_id, LOWER(patient_uid) AS normalized_patient_uid, count(*)
FROM patients
GROUP BY hospital_id, LOWER(patient_uid)
HAVING count(*) > 1;

SELECT hospital_id, LOWER(visit_code) AS normalized_visit_code, count(*)
FROM pacs_worklists
WHERE visit_code IS NOT NULL
  AND BTRIM(visit_code) <> ''
GROUP BY hospital_id, LOWER(visit_code)
HAVING count(*) > 1;

SELECT id, result_id, image_path
FROM pacs_result_images
WHERE image_path ~* '^[a-z][a-z0-9+.-]*://';

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
