CREATE TABLE IF NOT EXISTS pacs_viewer_states (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT md5(random()::text || clock_timestamp()::text)::uuid,
    hospital_id BIGINT NOT NULL,
    modality_id BIGINT,
    study_id BIGINT,
    worklist_id BIGINT,
    patient_id BIGINT,
    study_instance_uid VARCHAR(255),
    accession_number VARCHAR(255),
    patient_code VARCHAR(255),
    state_type VARCHAR(64) NOT NULL DEFAULT 'OHIF_VIEWER_STATE',
    schema_version INTEGER NOT NULL DEFAULT 1,
    viewer_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    measurements JSONB NOT NULL DEFAULT '[]'::jsonb,
    annotations JSONB NOT NULL DEFAULT '[]'::jsonb,
    segmentations JSONB NOT NULL DEFAULT '[]'::jsonb,
    additional_findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    version INTEGER NOT NULL DEFAULT 1,
    created_by BIGINT,
    modified_by BIGINT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_viewer_states_public_id
    ON pacs_viewer_states (public_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_viewer_states_current_worklist
    ON pacs_viewer_states (hospital_id, worklist_id, state_type)
    WHERE is_active = 1 AND worklist_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_viewer_states_current_study
    ON pacs_viewer_states (hospital_id, study_id, state_type)
    WHERE is_active = 1 AND worklist_id IS NULL AND study_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_viewer_states_current_study_uid
    ON pacs_viewer_states (hospital_id, study_instance_uid, state_type)
    WHERE is_active = 1 AND worklist_id IS NULL AND study_id IS NULL AND study_instance_uid IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pacs_viewer_states_patient
    ON pacs_viewer_states (hospital_id, patient_id, modified_at DESC)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_viewer_states_accession
    ON pacs_viewer_states (hospital_id, accession_number)
    WHERE is_active = 1 AND accession_number IS NOT NULL;
