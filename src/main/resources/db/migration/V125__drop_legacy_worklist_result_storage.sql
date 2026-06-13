-- Keep PACS report/result data in one normalized model.
-- Legacy pacs_worklist_results duplicated result text and image paths beside
-- pacs_results/pacs_result_images, so migrate any remaining rows and remove it.

ALTER TABLE pacs_results
    ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;

UPDATE pacs_results
SET modified_at = COALESCE(modified_at, updated_at, created_at, NOW())
WHERE modified_at IS NULL;

ALTER TABLE pacs_results
    ALTER COLUMN modified_at SET DEFAULT NOW(),
    ALTER COLUMN modified_at SET NOT NULL,
    DROP COLUMN IF EXISTS updated_at;

DROP INDEX IF EXISTS ux_pacs_result_templates_name_active;
DROP INDEX IF EXISTS idx_pacs_result_templates_hospital_modality;

ALTER TABLE pacs_result_templates
    ADD COLUMN IF NOT EXISTS is_active SMALLINT;

UPDATE pacs_result_templates
SET is_active = CASE WHEN active THEN 1 ELSE 2 END
WHERE is_active IS NULL;

ALTER TABLE pacs_result_templates
    ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;

UPDATE pacs_result_templates
SET modified_at = COALESCE(modified_at, updated_at, created_at, NOW())
WHERE modified_at IS NULL;

ALTER TABLE pacs_result_templates
    ALTER COLUMN is_active SET DEFAULT 1,
    ALTER COLUMN is_active SET NOT NULL,
    ALTER COLUMN modified_at SET DEFAULT NOW(),
    ALTER COLUMN modified_at SET NOT NULL,
    DROP COLUMN IF EXISTS active,
    DROP COLUMN IF EXISTS updated_at;

ALTER TABLE pacs_result_templates
    DROP CONSTRAINT IF EXISTS chk_pacs_result_templates_is_active,
    ADD CONSTRAINT chk_pacs_result_templates_is_active CHECK (is_active IN (1, 2));

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_result_templates_name_active
    ON pacs_result_templates (hospital_id, modality_id, LOWER(template_name))
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_result_templates_hospital_modality
    ON pacs_result_templates (hospital_id, modality_id, is_active, template_name);

WITH legacy_result AS (
    SELECT DISTINCT ON (qr.hospital_id, qr.worklist_id)
        qr.id AS legacy_id,
        qr.hospital_id,
        q.modality_id,
        COALESCE(q.study_id, qsl.study_id) AS study_id,
        qr.worklist_id,
        q.patient_id,
        qr.description AS result_text,
        qr.image_paths_json,
        qr.created_by,
        COALESCE(qr.created_at, qr.created, NOW()) AS created_at,
        COALESCE(qr.modified_at, qr.modified, qr.created_at, qr.created, NOW()) AS modified_at
    FROM pacs_worklist_results qr
    INNER JOIN pacs_worklists q
        ON q.id = qr.worklist_id
       AND q.hospital_id = qr.hospital_id
    LEFT JOIN pacs_worklist_study_links qsl
        ON qsl.hospital_id = q.hospital_id
       AND qsl.worklist_id = q.id
       AND qsl.is_primary = 1
    WHERE qr.is_active = 1
    ORDER BY
        qr.hospital_id,
        qr.worklist_id,
        COALESCE(qr.modified_at, qr.modified, qr.created_at, qr.created, NOW()) DESC,
        qr.id DESC
)
INSERT INTO pacs_results (
    hospital_id,
    modality_id,
    study_id,
    worklist_id,
    patient_id,
    result_date,
    result_text,
    status,
    completed,
    is_active,
    created_by,
    created_at,
    modified_at
)
SELECT
    lr.hospital_id,
    lr.modality_id,
    lr.study_id,
    lr.worklist_id,
    lr.patient_id,
    CAST(lr.created_at AS DATE),
    lr.result_text,
    'COMPLETED',
    TRUE,
    1,
    lr.created_by,
    lr.created_at,
    lr.modified_at
FROM legacy_result lr
WHERE NOT EXISTS (
    SELECT 1
    FROM pacs_results pr
    WHERE pr.hospital_id = lr.hospital_id
      AND pr.worklist_id = lr.worklist_id
      AND pr.is_active = 1
);

CREATE OR REPLACE FUNCTION pg_temp.safe_jsonb_array_text(value text)
RETURNS TABLE(image_path text, ordinality BIGINT)
LANGUAGE plpgsql
AS $$
BEGIN
    IF value IS NULL OR BTRIM(value) = '' THEN
        RETURN;
    END IF;

    RETURN QUERY
    SELECT element.value, element.ordinality
    FROM jsonb_array_elements_text(value::jsonb) WITH ORDINALITY AS element(value, ordinality);
EXCEPTION WHEN others THEN
    RETURN;
END;
$$;

WITH legacy_result AS (
    SELECT DISTINCT ON (qr.hospital_id, qr.worklist_id)
        qr.id AS legacy_id,
        qr.hospital_id,
        qr.worklist_id,
        qr.image_paths_json,
        COALESCE(qr.created_at, qr.created, NOW()) AS created_at
    FROM pacs_worklist_results qr
    WHERE qr.is_active = 1
    ORDER BY
        qr.hospital_id,
        qr.worklist_id,
        COALESCE(qr.modified_at, qr.modified, qr.created_at, qr.created, NOW()) DESC,
        qr.id DESC
),
legacy_image AS (
    SELECT
        pr.id AS result_id,
        BTRIM(image.image_path) AS image_path,
        (image.ordinality - 1)::INT AS sort_order,
        lr.created_at
    FROM legacy_result lr
    INNER JOIN pacs_results pr
        ON pr.hospital_id = lr.hospital_id
       AND pr.worklist_id = lr.worklist_id
       AND pr.is_active = 1
    CROSS JOIN LATERAL pg_temp.safe_jsonb_array_text(lr.image_paths_json) image
    WHERE NULLIF(BTRIM(image.image_path), '') IS NOT NULL
)
INSERT INTO pacs_result_images (
    result_id,
    image_path,
    original_file_name,
    file_type,
    file_size,
    sort_order,
    is_active,
    created_at
)
SELECT
    li.result_id,
    li.image_path,
    REGEXP_REPLACE(li.image_path, '^.*/', '') AS original_file_name,
    CASE
        WHEN LOWER(li.image_path) LIKE '%.png' THEN 'image/png'
        WHEN LOWER(li.image_path) LIKE '%.webp' THEN 'image/webp'
        WHEN LOWER(li.image_path) LIKE '%.jpg' THEN 'image/jpeg'
        WHEN LOWER(li.image_path) LIKE '%.jpeg' THEN 'image/jpeg'
        ELSE NULL
    END AS file_type,
    NULL,
    li.sort_order,
    1,
    li.created_at
FROM legacy_image li
WHERE NOT EXISTS (
    SELECT 1
    FROM pacs_result_images pi
    WHERE pi.result_id = li.result_id
      AND pi.image_path = li.image_path
      AND pi.is_active = 1
);

DROP INDEX IF EXISTS ux_pacs_worklist_results_hospital_worklist_active;
DROP INDEX IF EXISTS idx_pacs_worklist_results_hospital_active_created;
DROP INDEX IF EXISTS idx_worklist_results_description_trgm;
DROP INDEX IF EXISTS idx_worklist_results_hospital_active_id_desc;
DROP INDEX IF EXISTS idx_worklist_results_hospital_worklist_active;
DROP INDEX IF EXISTS idx_worklist_results_hospital_worklist_id_desc;

DROP TABLE IF EXISTS pacs_worklist_results;

DELETE FROM endpoint_permissions
WHERE endpoint_pattern IN (
    '/worklist/worklist-result-list',
    '/worklist/worklist-result-find/{id}',
    '/worklist/worklist-result-create',
    '/worklist/worklist-result-update',
    '/worklist/worklist-result-delete/{id}'
);

DELETE FROM role_module_details rmd
USING module_details md
WHERE rmd.module_detail_id = md.id
  AND (
      md.code ILIKE 'pacs.worklist.result.%'
      OR md.code ILIKE 'pacs.queue.result.%'
  );

DELETE FROM module_details
WHERE code ILIKE 'pacs.worklist.result.%'
   OR code ILIKE 'pacs.queue.result.%';

DELETE FROM modules m
WHERE m.code IN ('pacs-worklist-result', 'pacs-queue-result')
  AND NOT EXISTS (
      SELECT 1
      FROM module_details md
      WHERE md.module_id = m.id
  );

UPDATE users
SET
    permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;

COMMENT ON COLUMN pacs_results.modified_at IS 'Last modification timestamp for this PACS result.';
COMMENT ON COLUMN pacs_result_templates.is_active IS 'Standard active flag: 1 active, 2 inactive.';
