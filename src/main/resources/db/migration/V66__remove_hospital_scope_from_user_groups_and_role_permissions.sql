-- Remove hospital-scoped user-group membership.
-- This migration keeps PACS hospital domain tables intact and only changes auth/permission role linkage.

-- 1) Collapse duplicated memberships that differ only by hospital before dropping hospital_id.
WITH ranked_user_groups AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, role_id
            ORDER BY
                CASE WHEN is_active = 1 THEN 0 ELSE 1 END ASC,
                COALESCE(modified_at, modified, created_at, created) DESC,
                id DESC
        ) AS rn
    FROM user_groups
)
DELETE FROM user_groups ug
USING ranked_user_groups r
WHERE ug.id = r.id
  AND r.rn > 1;

-- 2) Remove hospital_id from user_groups and rebuild uniqueness/indexes for global role membership.
ALTER TABLE user_groups DROP COLUMN IF EXISTS hospital_id;

ALTER TABLE user_groups
    DROP CONSTRAINT IF EXISTS user_groups_user_id_role_id_key;

ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_user_id_role_id_key UNIQUE (user_id, role_id);

DROP INDEX IF EXISTS idx_user_groups_user_hospital;
DROP INDEX IF EXISTS idx_user_groups_hospital_role_active;
DROP INDEX IF EXISTS idx_user_groups_user_hospital_role;
DROP INDEX IF EXISTS idx_user_groups_user_hospital_active;

CREATE INDEX IF NOT EXISTS idx_user_groups_user_role_active
    ON user_groups(user_id, role_id, is_active);

CREATE INDEX IF NOT EXISTS idx_user_groups_role_active
    ON user_groups(role_id, is_active);

-- 3) Make roles global for permission/user-group flow (no hospital-scoped role ownership).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'roles'
          AND column_name = 'hospital_id'
    ) THEN
        UPDATE roles
        SET hospital_id = NULL
        WHERE hospital_id IS NOT NULL;
    END IF;
END $$;

DO $$
DECLARE
    role_name_unique_constraint text;
BEGIN
    SELECT tc.constraint_name
    INTO role_name_unique_constraint
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage k1
      ON tc.constraint_name = k1.constraint_name
     AND tc.table_schema = k1.table_schema
    JOIN information_schema.key_column_usage k2
      ON tc.constraint_name = k2.constraint_name
     AND tc.table_schema = k2.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = 'roles'
      AND tc.constraint_type = 'UNIQUE'
      AND k1.column_name = 'hospital_id'
      AND k2.column_name = 'name'
    LIMIT 1;

    IF role_name_unique_constraint IS NOT NULL THEN
        EXECUTE format('ALTER TABLE roles DROP CONSTRAINT %I', role_name_unique_constraint);
    END IF;
END $$;

-- Keep exactly one active role per normalized name.
WITH ranked_roles AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY LOWER(TRIM(COALESCE(name, '')))
            ORDER BY
                COALESCE(is_system_role, FALSE) DESC,
                COALESCE(modified_at, modified, created_at, created) DESC,
                id DESC
        ) AS rn
    FROM roles
    WHERE is_active = 1
)
UPDATE roles r
SET is_active = 2,
    modified = NOW(),
    modified_at = NOW()
FROM ranked_roles rr
WHERE r.id = rr.id
  AND rr.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_roles_active_name_global
    ON roles (LOWER(TRIM(COALESCE(name, ''))))
    WHERE is_active = 1;
