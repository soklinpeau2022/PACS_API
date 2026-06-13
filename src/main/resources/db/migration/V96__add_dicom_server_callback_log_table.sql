CREATE TABLE IF NOT EXISTS dicom_server_callback_log (
    id BIGSERIAL PRIMARY KEY,
    event VARCHAR(120),
    accession_number VARCHAR(255),
    dicom_server_study_id VARCHAR(255),
    dicom_server_patient_id VARCHAR(255),
    dicom_server_series_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    warning_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dicom_server_callback_log_accession
    ON dicom_server_callback_log (accession_number);

CREATE INDEX IF NOT EXISTS idx_dicom_server_callback_log_study
    ON dicom_server_callback_log (dicom_server_study_id);

CREATE INDEX IF NOT EXISTS idx_dicom_server_callback_log_received_at
    ON dicom_server_callback_log (received_at DESC);
