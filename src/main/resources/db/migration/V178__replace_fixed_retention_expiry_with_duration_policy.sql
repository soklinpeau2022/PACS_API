ALTER TABLE study_retention_policies
    ADD COLUMN IF NOT EXISTS retention_value INTEGER,
    ADD COLUMN IF NOT EXISTS retention_unit VARCHAR(20);

UPDATE study_retention_policies
SET retention_value = COALESCE(retention_value, retention_days),
    retention_unit = COALESCE(retention_unit, 'DAY')
WHERE retention_value IS NULL
   OR retention_unit IS NULL;

ALTER TABLE study_retention_policies
    ALTER COLUMN retention_value SET NOT NULL,
    ALTER COLUMN retention_unit SET NOT NULL;

ALTER TABLE study_retention_policies
    DROP COLUMN IF EXISTS fixed_expire_at;

ALTER TABLE study_retention_policies
    DROP CONSTRAINT IF EXISTS ck_study_retention_policies_retention_value,
    DROP CONSTRAINT IF EXISTS ck_study_retention_policies_retention_unit;

ALTER TABLE study_retention_policies
    ADD CONSTRAINT ck_study_retention_policies_retention_value CHECK (retention_value BETWEEN 1 AND 3650),
    ADD CONSTRAINT ck_study_retention_policies_retention_unit CHECK (retention_unit IN ('DAY', 'MONTH', 'YEAR'));

COMMENT ON COLUMN study_retention_policies.retention_value IS
    'Retention amount configured by UI, for example 5 with retention_unit MONTH.';

COMMENT ON COLUMN study_retention_policies.retention_unit IS
    'Retention unit configured by UI. Supported values: DAY, MONTH, YEAR.';
