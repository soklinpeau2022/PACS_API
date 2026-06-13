-- Relationship hardening
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_rotated_from
        FOREIGN KEY (rotated_from_id) REFERENCES refresh_tokens(id);

ALTER TABLE revoked_tokens
    ADD CONSTRAINT fk_revoked_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_revoked_tokens_hospital
        FOREIGN KEY (hospital_id) REFERENCES hospitals(id);

ALTER TABLE auth_audits
    ADD CONSTRAINT fk_auth_audits_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_auth_audits_hospital
        FOREIGN KEY (hospital_id) REFERENCES hospitals(id);

-- Allow only one active default hospital per user
CREATE UNIQUE INDEX ux_user_hospitals_one_default_active
    ON user_hospitals(user_id)
    WHERE is_default = TRUE AND is_active = 1;

-- Useful relationship indexes
CREATE INDEX idx_user_groups_hospital_role_active
    ON user_groups(hospital_id, role_id, is_active);

CREATE INDEX idx_role_module_details_role_id
    ON role_module_details(role_id);

CREATE INDEX idx_role_module_details_module_detail_id
    ON role_module_details(module_detail_id);

CREATE INDEX idx_endpoint_permissions_method_pattern_active
    ON endpoint_permissions(http_method, endpoint_pattern, is_active);

-- Endpoint aliases (kebab style) mapped to existing permission codes
INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code)
VALUES
    ('POST', '/user/user-list', 'user.view'),
    ('POST', '/user/user-create', 'user.add'),
    ('POST', '/user/user-update', 'user.edit'),
    ('POST', '/user/user-delete/*', 'user.delete'),
    ('POST', '/role/role-list', 'role.view'),
    ('POST', '/role/role-create', 'role.add'),
    ('POST', '/role/role-update', 'role.edit'),
    ('POST', '/permission/permission-tree', 'role.assign_permission'),
    ('POST', '/patient/patient-list', 'pacs.patient.view'),
    ('POST', '/patient/patient-create', 'pacs.patient.create'),
    ('POST', '/queue/queue-list', 'pacs.queue.view'),
    ('POST', '/queue/queue-return', 'pacs.queue.return'),
    ('POST', '/study/study-list', 'pacs.study.view'),
    ('POST', '/viewer/viewer-open', 'pacs.viewer.open')
ON CONFLICT (http_method, endpoint_pattern, permission_code) DO NOTHING;
