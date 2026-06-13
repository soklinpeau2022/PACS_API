-- Enforce single audit convention: modified_at / modified_by.
-- Backfill from legacy updated_at where present, then drop updated_at.

ALTER TABLE services ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
ALTER TABLE services ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE services
SET modified_at = COALESCE(modified_at, updated_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE services ALTER COLUMN modified_at SET DEFAULT NOW();
ALTER TABLE services DROP COLUMN IF EXISTS updated_at;

ALTER TABLE modalities ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
ALTER TABLE modalities ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE modalities
SET modified_at = COALESCE(modified_at, updated_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE modalities ALTER COLUMN modified_at SET DEFAULT NOW();
ALTER TABLE modalities DROP COLUMN IF EXISTS updated_at;

ALTER TABLE hospital_modalities ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
ALTER TABLE hospital_modalities ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE hospital_modalities
SET modified_at = COALESCE(modified_at, updated_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE hospital_modalities ALTER COLUMN modified_at SET DEFAULT NOW();
ALTER TABLE hospital_modalities DROP COLUMN IF EXISTS updated_at;

ALTER TABLE pacs_patient_queue ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
ALTER TABLE pacs_patient_queue ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE pacs_patient_queue
SET modified_at = COALESCE(modified_at, updated_at, created_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE pacs_patient_queue ALTER COLUMN modified_at SET DEFAULT NOW();
ALTER TABLE pacs_patient_queue DROP COLUMN IF EXISTS updated_at;

ALTER TABLE pacs_visit_sequences ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ;
ALTER TABLE pacs_visit_sequences ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE pacs_visit_sequences
SET modified_at = COALESCE(modified_at, updated_at, NOW())
WHERE modified_at IS NULL;
ALTER TABLE pacs_visit_sequences ALTER COLUMN modified_at SET DEFAULT NOW();
ALTER TABLE pacs_visit_sequences DROP COLUMN IF EXISTS updated_at;
