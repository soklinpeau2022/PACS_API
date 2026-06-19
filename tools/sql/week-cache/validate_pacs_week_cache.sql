\set ON_ERROR_STOP on
\pset pager off

SELECT
    to_regclass('public.pacs_worklists_week_cache') IS NOT NULL AS worklist_week_cache_exists,
    to_regclass('public.pacs_studies_week_cache') IS NOT NULL AS study_week_cache_exists;

SELECT
    'worklist_cache_heavy_error_column' AS check_name,
    column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'pacs_worklists_week_cache'
  AND column_name = 'error_message';

SELECT
    'worklist_cache_orphan' AS check_name,
    c.id
FROM pacs_worklists_week_cache c
LEFT JOIN pacs_worklists w ON w.id = c.id
WHERE w.id IS NULL
ORDER BY c.id
LIMIT 100;

SELECT
    'study_cache_orphan' AS check_name,
    c.id
FROM pacs_studies_week_cache c
LEFT JOIN pacs_studies s ON s.id = c.id
WHERE s.id IS NULL
ORDER BY c.id
LIMIT 100;

SELECT
    'worklist_cache_older_than_7_days' AS check_name,
    id,
    created_at
FROM pacs_worklists_week_cache
WHERE created_at < NOW() - INTERVAL '7 days'
ORDER BY created_at, id
LIMIT 100;

SELECT
    'study_cache_older_than_7_days' AS check_name,
    id,
    received_at,
    created_at,
    created
FROM pacs_studies_week_cache
WHERE COALESCE(image_received_at, received_at, created_at, created) < NOW() - INTERVAL '7 days'
ORDER BY COALESCE(image_received_at, received_at, created_at, created), id
LIMIT 100;

SELECT
    'worklist_cache_status_mismatch' AS check_name,
    c.id,
    c.status AS cache_status,
    w.status AS main_status
FROM pacs_worklists_week_cache c
JOIN pacs_worklists w ON w.id = c.id
WHERE c.status <> w.status
ORDER BY c.id
LIMIT 100;

SELECT
    'study_cache_accession_mismatch' AS check_name,
    c.id,
    c.accession_number AS cache_accession,
    s.accession_number AS main_accession
FROM pacs_studies_week_cache c
JOIN pacs_studies s ON s.id = c.id
WHERE COALESCE(c.accession_number, '') <> COALESCE(s.accession_number, '')
ORDER BY c.id
LIMIT 100;

SELECT
    'study_cache_status_mismatch' AS check_name,
    c.id,
    c.status AS cache_status,
    s.status AS main_status
FROM pacs_studies_week_cache c
JOIN pacs_studies s ON s.id = c.id
WHERE COALESCE(c.status, -1) <> COALESCE(s.status, -1)
ORDER BY c.id
LIMIT 100;

SELECT
    'worklist_cache_count' AS check_name,
    COUNT(*) AS total
FROM pacs_worklists_week_cache;

SELECT
    'study_cache_count' AS check_name,
    COUNT(*) AS total
FROM pacs_studies_week_cache;

SELECT
    'main_recent_worklists' AS check_name,
    COUNT(*) AS total
FROM pacs_worklists
WHERE COALESCE(created_at, created) >= NOW() - INTERVAL '7 days';

SELECT
    'main_recent_active_studies' AS check_name,
    COUNT(*) AS total
FROM pacs_studies
WHERE is_active = 1
  AND COALESCE(image_received_at, received_at, created_at, created) >= NOW() - INTERVAL '7 days';

SELECT
    'worklist_recent_missing_from_cache' AS check_name,
    w.id
FROM pacs_worklists w
LEFT JOIN pacs_worklists_week_cache c ON c.id = w.id
WHERE COALESCE(w.created_at, w.created) >= NOW() - INTERVAL '7 days'
  AND c.id IS NULL
ORDER BY w.id
LIMIT 100;

SELECT
    'study_recent_missing_from_cache' AS check_name,
    s.id
FROM pacs_studies s
LEFT JOIN pacs_studies_week_cache c ON c.id = s.id
WHERE s.is_active = 1
  AND COALESCE(s.image_received_at, s.received_at, s.created_at, s.created) >= NOW() - INTERVAL '7 days'
  AND c.id IS NULL
ORDER BY s.id
LIMIT 100;
