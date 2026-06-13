DELETE FROM endpoint_permissions
WHERE endpoint_pattern IN (
    '/viewer/viewer-open',
    '/viewer/viewer-validate',
    '/viewer/viewer-close'
)
   OR endpoint_pattern LIKE '/viewer/viewer-dicomweb%';

DELETE FROM role_module_details rmd
USING module_details md
WHERE rmd.module_detail_id = md.id
  AND md.code = 'pacs.viewer.open';

DELETE FROM module_details
WHERE code = 'pacs.viewer.open';

DELETE FROM modules m
USING modules target
LEFT JOIN module_details md ON md.module_id = target.id
WHERE m.id = target.id
  AND target.code = 'pacs-viewer'
  AND md.id IS NULL;

DELETE FROM module_types mt
USING module_types target
LEFT JOIN modules m ON m.module_type_id = target.id
WHERE mt.id = target.id
  AND target.code = 'PACS_VIEWER'
  AND m.id IS NULL;

UPDATE oauth2_clients
SET allowed_scopes = BTRIM(REGEXP_REPLACE(' ' || allowed_scopes || ' ', '\s+pacs\.viewer\.open\s+', ' ', 'g')),
    modified = NOW()
WHERE (' ' || allowed_scopes || ' ') LIKE '% pacs.viewer.open %';

DROP TABLE IF EXISTS pacs_viewer_sessions;
