CREATE TABLE IF NOT EXISTS study_retention_policies (
    id                   BIGSERIAL PRIMARY KEY,
    public_id            UUID NOT NULL DEFAULT md5(random()::text || clock_timestamp()::text)::uuid,
    hospital_id           BIGINT REFERENCES hospitals(id),
    dicom_server_id       BIGINT REFERENCES hospital_dicom_servers(id),
    modality_id           BIGINT REFERENCES modalities(id),
    retention_days        INTEGER NOT NULL,
    notify_before_days    INTEGER NOT NULL DEFAULT 14,
    require_approval      BOOLEAN NOT NULL DEFAULT TRUE,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    notes                 TEXT,
    is_active             SMALLINT NOT NULL DEFAULT 1,
    created_by            BIGINT REFERENCES users(id),
    modified_by           BIGINT REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_study_retention_policies_active CHECK (is_active IN (1, 2)),
    CONSTRAINT ck_study_retention_policies_retention_days CHECK (retention_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_study_retention_policies_notify_days CHECK (notify_before_days BETWEEN 0 AND 365)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_study_retention_policies_public_id
    ON study_retention_policies (public_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_study_retention_policies_scope_active
    ON study_retention_policies (
        COALESCE(hospital_id, 0),
        COALESCE(dicom_server_id, 0),
        COALESCE(modality_id, 0)
    )
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_study_retention_policies_scope
    ON study_retention_policies (hospital_id, dicom_server_id, modality_id, enabled, is_active);

CREATE TABLE IF NOT EXISTS study_retention_delete_requests (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT md5(random()::text || clock_timestamp()::text)::uuid,
    hospital_id            BIGINT REFERENCES hospitals(id),
    study_id               BIGINT,
    policy_id              BIGINT REFERENCES study_retention_policies(id) ON DELETE SET NULL,
    dicom_server_id        BIGINT REFERENCES hospital_dicom_servers(id),
    modality_id            BIGINT REFERENCES modalities(id),
    status                 VARCHAR(40) NOT NULL DEFAULT 'PENDING_APPROVAL',
    expires_at             TIMESTAMPTZ NOT NULL,
    near_expiry_at         TIMESTAMPTZ,
    study_instance_uid     VARCHAR(200),
    dicom_server_study_id  VARCHAR(255),
    accession_number       VARCHAR(100),
    reference_visit_code   VARCHAR(120),
    patient_mrn            VARCHAR(100),
    patient_name           VARCHAR(255),
    requested_by           BIGINT REFERENCES users(id),
    requested_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_by            BIGINT REFERENCES users(id),
    approved_at            TIMESTAMPTZ,
    rejected_by            BIGINT REFERENCES users(id),
    rejected_at            TIMESTAMPTZ,
    deleted_at             TIMESTAMPTZ,
    decision_note          TEXT,
    error_message          TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_study_retention_delete_requests_status CHECK (
        status IN (
            'PENDING_APPROVAL',
            'APPROVED',
            'DELETE_FAILED',
            'DELETED',
            'REJECTED',
            'KEEP_PERMANENT'
        )
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_study_retention_delete_requests_public_id
    ON study_retention_delete_requests (public_id);

CREATE INDEX IF NOT EXISTS idx_study_retention_delete_requests_study_latest
    ON study_retention_delete_requests (hospital_id, study_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_study_retention_delete_requests_status
    ON study_retention_delete_requests (hospital_id, status, updated_at DESC);

INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, 'study-retention', 'Study Retention', 7, 1, NOW()
FROM module_types mt
WHERE mt.code = 'HOSPITAL'
ON CONFLICT (code) DO UPDATE
SET module_type_id = EXCLUDED.module_type_id,
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('study-retention', 'study.retention.policy.view', 'Study Retention Policy (View)', 'VIEW', 'VIEW', 1),
        ('study-retention', 'study.retention.policy.add', 'Study Retention Policy (Add)', 'ADD', 'ADD', 2),
        ('study-retention', 'study.retention.policy.edit', 'Study Retention Policy (Edit)', 'EDIT', 'EDIT', 3),
        ('study-retention', 'study.retention.policy.delete', 'Study Retention Policy (Delete)', 'DELETE', 'DELETE', 4),
        ('study-retention', 'study.retention.approval.view', 'Study Retention Approval (View)', 'VIEW', 'VIEW', 5),
        ('study-retention', 'study.retention.approval.approve', 'Study Retention Approval (Approve Delete)', 'APPROVE', 'APPROVE', 6)
)
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, d.permission_code, d.permission_name, d.permission_type, d.action_key, d.display_order, 1, NOW()
FROM desired_details d
JOIN modules m ON m.code = d.module_code
ON CONFLICT (code) DO UPDATE
SET module_id = EXCLUDED.module_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    action_key = EXCLUDED.action_key,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/study-retention/policy-list', 'study.retention.policy.view', 'pacs.api', 1),
    ('POST', '/study-retention/policy-save', 'study.retention.policy.edit', 'pacs.api', 1),
    ('POST', '/study-retention/policy-delete/*', 'study.retention.policy.delete', 'pacs.api', 1),
    ('POST', '/study-retention/review-list', 'study.retention.approval.view', 'pacs.api', 1),
    ('POST', '/study-retention/summary', 'study.retention.approval.view', 'pacs.api', 1),
    ('POST', '/study-retention/approve-delete/*', 'study.retention.approval.approve', 'pacs.api', 1),
    ('POST', '/study-retention/reject-delete/*', 'study.retention.approval.approve', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT r.id, md.id, 1, NOW()
FROM roles r
JOIN module_details md ON md.code IN (
    'study.retention.policy.view',
    'study.retention.policy.add',
    'study.retention.policy.edit',
    'study.retention.policy.delete',
    'study.retention.approval.view',
    'study.retention.approval.approve'
)
WHERE r.is_active = 1
  AND (
      UPPER(COALESCE(r.code, '')) IN ('ADMIN', 'SUPER_ADMIN', 'SYSTEM_ADMIN')
      OR LOWER(TRIM(COALESCE(r.name, ''))) IN ('admin', 'super admin', 'system admin')
  )
ON CONFLICT (role_id, module_detail_id) DO NOTHING;

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;
