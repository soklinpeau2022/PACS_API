INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'pacs-service', 'PACS Service', 2, 1, NOW()
FROM module_types mt
WHERE mt.code = 'PACS_QUEUE'
ON CONFLICT (code) DO NOTHING;

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('pacs-service', 'service.view', 'Service (View)', 'VIEW', 'VIEW', 1),
        ('pacs-service', 'service.add', 'Service (Add)', 'ADD', 'ADD', 2),
        ('pacs-service', 'service.edit', 'Service (Edit)', 'EDIT', 'EDIT', 3),
        ('pacs-service', 'service.delete', 'Service (Delete)', 'DELETE', 'DELETE', 4),
        ('pacs-queue', 'pacs.queue.assign', 'PACS Queue (Assign)', 'ADD', 'ASSIGN', 5),
        ('pacs-queue', 'pacs.queue.send', 'PACS Queue (Send To PACS)', 'ACTION', 'SEND_TO_PACS', 6),
        ('pacs-queue', 'pacs.queue.receive', 'PACS Queue (Receive Study)', 'ACTION', 'RECEIVE_STUDY', 7),
        ('pacs-queue', 'pacs.queue.view_study', 'PACS Queue (View Study)', 'VIEW', 'VIEW_STUDY', 8),
        ('pacs-queue', 'pacs.queue.translate', 'PACS Queue (Translate)', 'ACTION', 'TRANSLATE', 9)
)
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, d.permission_code, d.permission_name, d.permission_type, d.action_key, d.display_order, 1, NOW()
FROM desired_details d
JOIN modules m ON m.code = d.module_code
ON CONFLICT (code) DO NOTHING;

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, is_active)
VALUES
    ('POST', '/service/service-list', 'service.view', 1),
    ('POST', '/service/service-find/*', 'service.view', 1),
    ('POST', '/service/service-create', 'service.add', 1),
    ('POST', '/service/service-update', 'service.edit', 1),
    ('POST', '/service/service-delete/*', 'service.delete', 1),
    ('POST', '/queue/queue-assign', 'pacs.queue.assign', 1),
    ('POST', '/queue/queue-send-to-pacs', 'pacs.queue.send', 1),
    ('POST', '/queue/queue-received-study', 'pacs.queue.receive', 1),
    ('POST', '/queue/queue-view-study', 'pacs.queue.view_study', 1),
    ('POST', '/queue/queue-translate', 'pacs.queue.translate', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created)
SELECT r.id, md.id, NOW()
FROM roles r
JOIN module_details md ON md.is_active = 1
WHERE r.code = 'ADMIN'
  AND r.is_active = 1
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users u
SET permission_version = COALESCE(u.permission_version, 0) + 1,
    modified = NOW()
WHERE EXISTS (
    SELECT 1
    FROM user_groups ug
    JOIN roles r ON r.id = ug.role_id
    WHERE ug.user_id = u.id
      AND ug.is_active = 1
      AND r.code = 'ADMIN'
      AND r.is_active = 1
);
