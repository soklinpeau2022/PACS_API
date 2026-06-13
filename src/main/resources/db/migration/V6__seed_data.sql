INSERT INTO hospitals (code, name, ae_title, timezone)
VALUES ('H001', 'Main Hospital', 'H001_PACS', 'Asia/Phnom_Penh');

INSERT INTO module_types (code, name, display_order) VALUES
  ('HOME',         'Home',            1),
  ('USER',         'User',            2),
  ('ROLE',         'Role',            3),
  ('HOSPITAL',     'Hospital',        4),
  ('PACS_PATIENT', 'PACS Patient',    10),
  ('PACS_QUEUE',   'PACS Queue',      11),
  ('PACS_STUDY',   'PACS Study',      12),
  ('PACS_VIEWER',  'PACS Viewer',     13),
  ('PACS_REPORT',  'PACS Report',     14);

INSERT INTO modules (module_type_id, code, name, display_order)
SELECT mt.id, 'home', 'Home', 1 FROM module_types mt WHERE mt.code = 'HOME';

INSERT INTO module_details (module_id, code, name, type, action_key, display_order)
SELECT m.id, 'home.view', 'Home (View)', 'VIEW', 'VIEW', 1
FROM modules m WHERE m.code = 'home';

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code) VALUES
  ('POST', '/user/list',          'user.view'),
  ('POST', '/user/create',        'user.add'),
  ('POST', '/user/update',        'user.edit'),
  ('POST', '/user/delete/*',      'user.delete'),
  ('POST', '/role/list',          'role.view'),
  ('POST', '/role/create',        'role.add'),
  ('POST', '/role/update',        'role.edit'),
  ('POST', '/permission/tree',    'role.assign_permission'),
  ('POST', '/patient/list',       'pacs.patient.view'),
  ('POST', '/patient/create',     'pacs.patient.create'),
  ('POST', '/queue/list',         'pacs.queue.view'),
  ('POST', '/queue/return',       'pacs.queue.return'),
  ('POST', '/study/list',         'pacs.study.view'),
  ('POST', '/viewer/open',        'pacs.viewer.open');

INSERT INTO roles (hospital_id, code, name, is_system_role)
VALUES (NULL, 'ADMIN', 'System Admin', TRUE);

INSERT INTO users (username, email, password, first_name, last_name, user_type, is_active)
VALUES ('admin', 'admin@pacs.local',
        '$2a$12$RxyCeKC/HoKvpGxL3NX1tuVOAHW1glRqFAJuvJbVQCOKS7YeAuRJa',
        'System', 'Admin', 9, 1);

INSERT INTO user_hospitals (user_id, hospital_id, is_default, is_active)
SELECT u.id, h.id, TRUE, 1
FROM users u, hospitals h
WHERE u.username = 'admin' AND h.code = 'H001';

INSERT INTO user_groups (user_id, hospital_id, role_id, is_active)
SELECT u.id, h.id, r.id, 1
FROM users u, hospitals h, roles r
WHERE u.username = 'admin' AND h.code = 'H001' AND r.code = 'ADMIN';
