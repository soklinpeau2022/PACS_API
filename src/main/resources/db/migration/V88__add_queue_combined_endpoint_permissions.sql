-- Keep the new combined queue endpoints visible to the permission filter.
-- We intentionally reuse the existing queue permission catalog so current roles
-- do not need a second permission migration just to access the renamed actions.

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/queue/queue-find', 'pacs.queue.view', 'pacs.api', 1),
    ('POST', '/queue/queue-update', 'pacs.queue.assign', 'pacs.api', 1),
    ('POST', '/queue/queue-start', 'pacs.queue.send', 'pacs.api', 1),
    ('POST', '/queue/queue-sync-result', 'pacs.queue.receive', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;
