ALTER TABLE study_retention_policies
    ADD COLUMN IF NOT EXISTS fixed_expire_at TIMESTAMPTZ;

COMMENT ON COLUMN study_retention_policies.fixed_expire_at IS
    'Optional fixed expiry timestamp. When set, matching studies expire at this date/time instead of received date plus retention_days.';
