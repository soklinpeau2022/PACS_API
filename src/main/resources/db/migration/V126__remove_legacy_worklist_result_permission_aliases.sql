-- V125 removed the legacy Worklist result controller and exact endpoint rows.
-- Older catalogs also kept wildcard aliases for find/delete; remove them too.

DELETE FROM endpoint_permissions
WHERE endpoint_pattern IN (
    '/worklist/worklist-result-find/*',
    '/worklist/worklist-result-delete/*'
)
   OR permission_code ILIKE 'pacs.worklist.result.%'
   OR permission_code ILIKE 'pacs.queue.result.%';

UPDATE users
SET
    permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
