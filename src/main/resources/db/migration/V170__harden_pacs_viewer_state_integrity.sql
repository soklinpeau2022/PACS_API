DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_hospital'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_hospital
            FOREIGN KEY (hospital_id) REFERENCES hospitals(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_modality'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_modality
            FOREIGN KEY (modality_id) REFERENCES modalities(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_study'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_study
            FOREIGN KEY (study_id) REFERENCES pacs_studies(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_worklist'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_worklist
            FOREIGN KEY (worklist_id) REFERENCES pacs_worklists(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_patient'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_patient
            FOREIGN KEY (patient_id) REFERENCES patients(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_created_by'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_created_by
            FOREIGN KEY (created_by) REFERENCES users(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_modified_by'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_modified_by
            FOREIGN KEY (modified_by) REFERENCES users(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_pacs_viewer_states_state_type'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT ck_pacs_viewer_states_state_type
            CHECK (state_type ~ '^[A-Z0-9][A-Z0-9_-]{0,63}$') NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_pacs_viewer_states_schema_version'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT ck_pacs_viewer_states_schema_version
            CHECK (schema_version BETWEEN 1 AND 1000) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_pacs_viewer_states_version'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT ck_pacs_viewer_states_version
            CHECK (version > 0) NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pacs_viewer_states_accession_scope
    ON pacs_viewer_states (hospital_id, accession_number, state_type, modified_at DESC)
    WHERE is_active = 1 AND accession_number IS NOT NULL;
