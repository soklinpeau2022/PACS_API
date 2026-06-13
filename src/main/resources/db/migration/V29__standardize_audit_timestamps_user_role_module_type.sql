-- Standardize audit timestamps to created_at / modified_at for user/role/module-type flow.
-- Backward-safe: old created/modified columns remain in place.

-- USERS
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE users
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE users
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT NOW();

-- HOSPITALS
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE hospitals
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE hospitals
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE hospitals ALTER COLUMN created_at SET DEFAULT NOW();

-- ROLES
ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE roles
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE roles
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE roles ALTER COLUMN created_at SET DEFAULT NOW();

-- MODULE TYPES
ALTER TABLE module_types ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE module_types ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE module_types
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE module_types
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE module_types ALTER COLUMN created_at SET DEFAULT NOW();

-- MODULES
ALTER TABLE modules ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE modules ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE modules
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE modules
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE modules ALTER COLUMN created_at SET DEFAULT NOW();

-- MODULE DETAILS
ALTER TABLE module_details ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE module_details ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE module_details
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE module_details
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE module_details ALTER COLUMN created_at SET DEFAULT NOW();

-- USER GROUPS
ALTER TABLE user_groups ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE user_groups ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE user_groups
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE user_groups
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE user_groups ALTER COLUMN created_at SET DEFAULT NOW();

-- USER HOSPITALS
ALTER TABLE user_hospitals ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE user_hospitals ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE user_hospitals
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
UPDATE user_hospitals
SET modified_at = COALESCE(modified_at, modified)
WHERE modified_at IS NULL;
ALTER TABLE user_hospitals ALTER COLUMN created_at SET DEFAULT NOW();

-- ROLE MODULE DETAILS
ALTER TABLE role_module_details ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE role_module_details ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE role_module_details
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;
ALTER TABLE role_module_details ALTER COLUMN created_at SET DEFAULT NOW();

-- Performance indexes for frequent list/filter operations
CREATE INDEX IF NOT EXISTS idx_users_active_created_at_desc
    ON users(is_active, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_roles_hospital_active_created_at_desc
    ON roles(hospital_id, is_active, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_hospitals_active_created_at_desc
    ON hospitals(is_active, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_module_types_active_display_order
    ON module_types(is_active, display_order, id);
