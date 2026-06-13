-- Remove legacy and non-standard artifacts not used in current PACS schema.
-- Safe for both fresh and existing databases.

-- Remove optional auth identity columns if they exist (legacy variants).
ALTER TABLE users DROP COLUMN IF EXISTS google_id;
ALTER TABLE users DROP COLUMN IF EXISTS google_auth;
ALTER TABLE users DROP COLUMN IF EXISTS telegram_id;
ALTER TABLE users DROP COLUMN IF EXISTS tele_user_id;

-- Remove employee link from users to keep user schema minimal.
ALTER TABLE users DROP COLUMN IF EXISTS employee_id;

-- Remove currency columns if they exist on hospitals.
ALTER TABLE hospitals DROP COLUMN IF EXISTS currency_center_id;
ALTER TABLE hospitals DROP COLUMN IF EXISTS base_currency_id;

-- Remove legacy tables from older company-based schema.
DROP TABLE IF EXISTS company_with_categories CASCADE;
DROP TABLE IF EXISTS company_categories CASCADE;
DROP TABLE IF EXISTS user_companies CASCADE;
DROP TABLE IF EXISTS currency_centers CASCADE;
DROP TABLE IF EXISTS companies CASCADE;
