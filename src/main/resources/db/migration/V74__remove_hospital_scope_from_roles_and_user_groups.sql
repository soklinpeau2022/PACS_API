-- Remove hospital scope from Role/UserGroup data model.

-- 1) Collapse duplicate user-group memberships that differ only by hospital.
WITH ranked_user_groups AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, role_id
            ORDER BY
                CASE WHEN is_active = 1 THEN 0 ELSE 1 END,
                COALESCE(modified_at, modified, created_at, created) DESC NULLS LAST,
                id DESC
        ) AS rn
    FROM user_groups
)
DELETE FROM user_groups ug
USING ranked_user_groups r
WHERE ug.id = r.id
  AND r.rn > 1;

-- 2) Drop hospital scope from user_groups.
ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS user_groups_hospital_id_fkey;
ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS user_groups_user_id_hospital_id_role_id_key;
ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS user_groups_user_id_role_id_key;

DROP INDEX IF EXISTS idx_user_groups_hospital_role_active;
DROP INDEX IF EXISTS idx_user_groups_user_hospital;
DROP INDEX IF EXISTS idx_user_groups_user_hospital_active;
DROP INDEX IF EXISTS idx_user_groups_user_hospital_role;

ALTER TABLE user_groups DROP COLUMN IF EXISTS hospital_id;

ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_user_id_role_id_key UNIQUE (user_id, role_id);

CREATE INDEX IF NOT EXISTS idx_user_groups_user_role_active
    ON user_groups (user_id, role_id, is_active);

CREATE INDEX IF NOT EXISTS idx_user_groups_role_active
    ON user_groups (role_id, is_active);

-- 3) Drop hospital scope from roles.
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_hospital_id_fkey;
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_hospital_id_name_key;

DROP INDEX IF EXISTS idx_roles_hospital_active_created_at_desc;
DROP INDEX IF EXISTS idx_roles_hospital_id;

ALTER TABLE roles DROP COLUMN IF EXISTS hospital_id;

CREATE INDEX IF NOT EXISTS idx_roles_active_created_at_desc
    ON roles (is_active, created_at DESC, id DESC);
