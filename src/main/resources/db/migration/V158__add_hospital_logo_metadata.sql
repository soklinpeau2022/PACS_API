ALTER TABLE hospitals
    ADD COLUMN IF NOT EXISTS logo_path TEXT,
    ADD COLUMN IF NOT EXISTS logo_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS logo_file_type VARCHAR(80),
    ADD COLUMN IF NOT EXISTS logo_file_size BIGINT,
    ADD COLUMN IF NOT EXISTS logo_updated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_hospitals_logo_updated_at
    ON hospitals (logo_updated_at)
    WHERE logo_path IS NOT NULL AND is_active = 1;
