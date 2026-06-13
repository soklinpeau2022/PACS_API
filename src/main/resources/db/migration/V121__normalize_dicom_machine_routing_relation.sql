WITH route_machine_seed AS (
    SELECT DISTINCT ON (r.hospital_id, LOWER(COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text)))
        r.hospital_id,
        r.modality_id,
        COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text) AS machine_code,
        COALESCE(NULLIF(BTRIM(r.machine_name), ''), NULLIF(BTRIM(r.machine_ae_title), ''), 'Machine ' || r.id::text) AS machine_name,
        NULLIF(BTRIM(r.room_name), '') AS room_name,
        COALESCE(NULLIF(BTRIM(r.machine_ae_title), ''), COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text)) AS machine_ae_title,
        COALESCE(NULLIF(BTRIM(r.machine_host), ''), '127.0.0.1') AS machine_host,
        COALESCE(r.machine_port, 104) AS machine_port,
        r.created_by,
        r.modified_by,
        r.created_at,
        r.modified_at
    FROM hospital_modality_server_routes r
    WHERE r.is_active != 2
      AND r.machine_id IS NULL
    ORDER BY r.hospital_id,
             LOWER(COALESCE(NULLIF(BTRIM(r.machine_code), ''), 'MACHINE_' || r.id::text)),
             r.id ASC
)
INSERT INTO hospital_dicom_machines (
    hospital_id,
    modality_id,
    machine_code,
    machine_name,
    room_name,
    machine_ae_title,
    machine_host,
    machine_port,
    is_active,
    created_by,
    modified_by,
    created_at,
    modified_at
)
SELECT
    seed.hospital_id,
    seed.modality_id,
    seed.machine_code,
    seed.machine_name,
    seed.room_name,
    seed.machine_ae_title,
    seed.machine_host,
    seed.machine_port,
    1,
    seed.created_by,
    seed.modified_by,
    seed.created_at,
    seed.modified_at
FROM route_machine_seed seed
WHERE EXISTS (
    SELECT 1
    FROM hospital_modalities hm
    WHERE hm.hospital_id = seed.hospital_id
      AND hm.modality_id = seed.modality_id
)
AND NOT EXISTS (
    SELECT 1
    FROM hospital_dicom_machines machine
    WHERE machine.hospital_id = seed.hospital_id
      AND LOWER(machine.machine_code) = LOWER(seed.machine_code)
      AND machine.is_active = 1
);

UPDATE hospital_modality_server_routes route
SET machine_id = machine.id
FROM hospital_dicom_machines machine
WHERE route.machine_id IS NULL
  AND route.is_active != 2
  AND route.hospital_id = machine.hospital_id
  AND route.modality_id = machine.modality_id
  AND LOWER(COALESCE(NULLIF(BTRIM(route.machine_code), ''), 'MACHINE_' || route.id::text)) = LOWER(machine.machine_code)
  AND machine.is_active = 1;

ALTER TABLE hospital_modality_server_routes
    DROP CONSTRAINT IF EXISTS chk_hmsr_machine_port_positive;

DROP INDEX IF EXISTS ux_hmsr_active_machine_code;
DROP INDEX IF EXISTS idx_hmsr_active_machine_ae;
DROP INDEX IF EXISTS idx_hmsr_hospital_modality_machine_active;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_dicom_machines'
          AND constraint_name = 'ux_hdm_id_hospital_modality'
    ) THEN
        ALTER TABLE hospital_dicom_machines
            ADD CONSTRAINT ux_hdm_id_hospital_modality
                UNIQUE (id, hospital_id, modality_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'chk_hmsr_active_machine_required'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT chk_hmsr_active_machine_required
                CHECK (is_active = 2 OR machine_id IS NOT NULL);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
          AND constraint_name = 'fk_hmsr_machine_hospital_modality'
    ) THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT fk_hmsr_machine_hospital_modality
                FOREIGN KEY (machine_id, hospital_id, modality_id)
                REFERENCES hospital_dicom_machines(id, hospital_id, modality_id);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hmsr_active_machine
    ON hospital_modality_server_routes (hospital_id, machine_id)
    WHERE is_active = 1
      AND machine_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_hmsr_hospital_modality_machine_active
    ON hospital_modality_server_routes (hospital_id, modality_id, is_active, machine_id, dicom_server_id, id);

ALTER TABLE hospital_modality_server_routes
    DROP COLUMN IF EXISTS machine_code,
    DROP COLUMN IF EXISTS machine_name,
    DROP COLUMN IF EXISTS machine_ae_title,
    DROP COLUMN IF EXISTS machine_host,
    DROP COLUMN IF EXISTS machine_port,
    DROP COLUMN IF EXISTS room_name;

WITH desired_module_types(code, name, display_order, group_code, group_name, group_order) AS (
    VALUES ('DICOM_MACHINE', 'DICOM Machine / Rooms', 43, 'DICOMCONFIG', 'DICOM Config', 5)
)
INSERT INTO module_types (code, name, display_order, menu_group_code, menu_group_name, menu_group_order, is_active, created)
SELECT code, name, display_order, group_code, group_name, group_order, 1, NOW()
FROM desired_module_types
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    menu_group_code = EXCLUDED.menu_group_code,
    menu_group_name = EXCLUDED.menu_group_name,
    menu_group_order = EXCLUDED.menu_group_order,
    is_active = 1,
    modified = NOW();

WITH desired_modules(module_type_code, code, name, display_order) AS (
    VALUES ('DICOM_MACHINE', 'dicom-machine', 'DICOM Machine / Rooms', 1)
)
INSERT INTO modules (module_type_id, code, name, display_order, is_active, created)
SELECT mt.id, dm.code, dm.name, dm.display_order, 1, NOW()
FROM desired_modules dm
JOIN module_types mt ON mt.code = dm.module_type_code
ON CONFLICT (code) DO UPDATE
SET module_type_id = EXCLUDED.module_type_id,
    name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

WITH desired_details(module_code, permission_code, permission_name, permission_type, action_key, display_order) AS (
    VALUES
        ('dicom-machine', 'dicom.machine.view', 'DICOM Machine / Rooms (View)', 'VIEW', 'VIEW', 1),
        ('dicom-machine', 'dicom.machine.add', 'DICOM Machine / Rooms (Add)', 'ADD', 'ADD', 2),
        ('dicom-machine', 'dicom.machine.edit', 'DICOM Machine / Rooms (Edit)', 'EDIT', 'EDIT', 3),
        ('dicom-machine', 'dicom.machine.delete', 'DICOM Machine / Rooms (Delete)', 'DELETE', 'DELETE', 4)
)
INSERT INTO module_details (module_id, code, name, type, action_key, display_order, is_active, created)
SELECT m.id, dd.permission_code, dd.permission_name, dd.permission_type, dd.action_key, dd.display_order, 1, NOW()
FROM desired_details dd
JOIN modules m ON m.code = dd.module_code
ON CONFLICT (code) DO UPDATE
SET module_id = EXCLUDED.module_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    action_key = EXCLUDED.action_key,
    display_order = EXCLUDED.display_order,
    is_active = 1,
    modified = NOW();

WITH copied_permissions(old_code, new_code) AS (
    VALUES
        ('dicom.routing.view', 'dicom.machine.view'),
        ('dicom.routing.add', 'dicom.machine.add'),
        ('dicom.routing.edit', 'dicom.machine.edit'),
        ('dicom.routing.delete', 'dicom.machine.delete')
)
INSERT INTO role_module_details (role_id, module_detail_id, created_by, created)
SELECT DISTINCT rmd.role_id, new_md.id, rmd.created_by, NOW()
FROM role_module_details rmd
JOIN module_details old_md ON old_md.id = rmd.module_detail_id
JOIN copied_permissions cp ON cp.old_code = old_md.code
JOIN module_details new_md ON new_md.code = cp.new_code
ON CONFLICT DO NOTHING;

INSERT INTO endpoint_permissions (http_method, endpoint_pattern, permission_code, required_scope, is_active)
VALUES
    ('POST', '/dicom-machine/dicom-machine-list', 'dicom.machine.view', 'pacs.api', 1),
    ('POST', '/dicom-machine/dicom-machine-find/*', 'dicom.machine.view', 'pacs.api', 1),
    ('POST', '/dicom-machine/dicom-machine-create', 'dicom.machine.add', 'pacs.api', 1),
    ('POST', '/dicom-machine/dicom-machine-update', 'dicom.machine.edit', 'pacs.api', 1),
    ('POST', '/dicom-machine/dicom-machine-delete/*', 'dicom.machine.delete', 'pacs.api', 1)
ON CONFLICT (http_method, endpoint_pattern, permission_code)
DO UPDATE SET
    required_scope = EXCLUDED.required_scope,
    is_active = 1;

UPDATE endpoint_permissions
SET is_active = 2
WHERE endpoint_pattern IN (
    '/dicom-routing/dicom-machine-list',
    '/dicom-routing/dicom-machine-find/{id}',
    '/dicom-routing/dicom-machine-create',
    '/dicom-routing/dicom-machine-update',
    '/dicom-routing/dicom-machine-delete/{id}'
);

UPDATE users
SET permission_version = COALESCE(permission_version, 0) + 1,
    modified = NOW()
WHERE is_active = 1;

COMMENT ON TABLE hospital_dicom_machines IS 'Reusable physical modality machines and rooms per hospital. DICOM routing references these rows by machine_id.';
COMMENT ON COLUMN hospital_modality_server_routes.machine_id IS 'Required machine/room relation for active routing rows.';
