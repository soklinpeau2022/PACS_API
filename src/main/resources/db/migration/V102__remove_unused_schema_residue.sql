-- Remove schema residue that is no longer used by the API or frontend.
-- Keep this migration narrow: it only drops empty/unused audit residue and
-- columns verified as unused by code, mappers, and live endpoint flows.

DROP TABLE IF EXISTS auth_audits CASCADE;

ALTER TABLE users
    DROP COLUMN IF EXISTS verification_code,
    DROP COLUMN IF EXISTS verification_code_expiry,
    DROP COLUMN IF EXISTS failed_login_count,
    DROP COLUMN IF EXISTS password_changed_at;

ALTER TABLE modules
    DROP COLUMN IF EXISTS route_path;

ALTER TABLE countries
    DROP COLUMN IF EXISTS iso2_code,
    DROP COLUMN IF EXISTS iso3_code;
