DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'hospital_modality_server_routes'
    ) THEN
        DROP INDEX IF EXISTS idx_hmsr_hospital_modality_active;
        DROP INDEX IF EXISTS idx_hmsr_hospital_modality_default_priority;
        DROP INDEX IF EXISTS ux_hmsr_one_default_active;

        ALTER TABLE hospital_modality_server_routes
            DROP CONSTRAINT IF EXISTS chk_hmsr_priority_positive,
            DROP CONSTRAINT IF EXISTS chk_hmsr_weight_positive,
            DROP CONSTRAINT IF EXISTS chk_hmsr_is_default;

        ALTER TABLE hospital_modality_server_routes
            DROP COLUMN IF EXISTS priority,
            DROP COLUMN IF EXISTS weight,
            DROP COLUMN IF EXISTS is_default;

        CREATE INDEX IF NOT EXISTS idx_hmsr_hospital_modality_active
            ON hospital_modality_server_routes (hospital_id, modality_id, is_active, id);
    END IF;
END $$;
