ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS abbr VARCHAR(20);
ALTER TABLE modalities ADD COLUMN IF NOT EXISTS abbr VARCHAR(20);
ALTER TABLE services ADD COLUMN IF NOT EXISTS abbr VARCHAR(20);

UPDATE hospitals SET abbr = UPPER(LEFT(COALESCE(code, 'HOSP'), 20)) WHERE abbr IS NULL OR TRIM(abbr) = '';
UPDATE modalities SET abbr = UPPER(LEFT(COALESCE(name, 'OT'), 20)) WHERE abbr IS NULL OR TRIM(abbr) = '';
UPDATE services SET abbr = UPPER(LEFT(COALESCE(code, name, 'SRV'), 20)) WHERE abbr IS NULL OR TRIM(abbr) = '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_hospitals_abbr_active ON hospitals (LOWER(abbr)) WHERE is_active = 1;
CREATE UNIQUE INDEX IF NOT EXISTS ux_modalities_abbr_active ON modalities (LOWER(abbr)) WHERE is_active = 1;
CREATE UNIQUE INDEX IF NOT EXISTS ux_services_abbr_active ON services (LOWER(abbr)) WHERE is_active = 1;
