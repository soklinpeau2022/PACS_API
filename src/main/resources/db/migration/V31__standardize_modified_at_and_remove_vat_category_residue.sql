-- Standardize audit timestamp columns and remove residual VAT/Currency/Category schema artifacts.
-- PostgreSQL-safe and idempotent.

-- 1) Standardize to modified_at.
ALTER TABLE modalities ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE modalities
SET modified_at = COALESCE(modified_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE modalities ALTER COLUMN modified_at SET DEFAULT NOW();

ALTER TABLE services ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE services
SET modified_at = COALESCE(modified_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE services ALTER COLUMN modified_at SET DEFAULT NOW();

ALTER TABLE hospital_modalities ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE hospital_modalities
SET modified_at = COALESCE(modified_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE hospital_modalities ALTER COLUMN modified_at SET DEFAULT NOW();

ALTER TABLE pacs_patient_queue ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
UPDATE pacs_patient_queue
SET modified_at = COALESCE(modified_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE pacs_patient_queue ALTER COLUMN modified_at SET DEFAULT NOW();

-- 2) Remove residual VAT/Currency/Category columns from any table when found.
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT table_schema, table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND column_name IN ('vat_calculate', 'vat_percent', 'base_currency_id', 'currency_center_id', 'category_id')
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP COLUMN IF EXISTS %I CASCADE',
            r.table_schema, r.table_name, r.column_name
        );
    END LOOP;
END $$;

-- 3) Drop legacy tables if they still exist.
DROP TABLE IF EXISTS vat_settings CASCADE;
DROP TABLE IF EXISTS hospital_categories CASCADE;
DROP TABLE IF EXISTS company_categories CASCADE;
DROP TABLE IF EXISTS company_with_categories CASCADE;
DROP TABLE IF EXISTS currency_centers CASCADE;

-- 4) Add indexes to speed creator/modifier joins and common relation filters.
CREATE INDEX IF NOT EXISTS idx_hospitals_created_by ON hospitals(created_by);
CREATE INDEX IF NOT EXISTS idx_hospitals_modified_by ON hospitals(modified_by);
CREATE INDEX IF NOT EXISTS idx_users_created_by ON users(created_by);
CREATE INDEX IF NOT EXISTS idx_users_modified_by ON users(modified_by);
CREATE INDEX IF NOT EXISTS idx_services_created_by ON services(created_by);
CREATE INDEX IF NOT EXISTS idx_services_modified_by ON services(modified_by);
CREATE INDEX IF NOT EXISTS idx_modalities_created_by ON modalities(created_by);
CREATE INDEX IF NOT EXISTS idx_modalities_modified_by ON modalities(modified_by);
CREATE INDEX IF NOT EXISTS idx_user_hospitals_active_hospital_user
    ON user_hospitals(is_active, hospital_id, user_id);
