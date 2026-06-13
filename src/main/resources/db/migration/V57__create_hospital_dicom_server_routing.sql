CREATE TABLE IF NOT EXISTS hospital_dicom_servers (
    id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    name VARCHAR(255) NOT NULL,
    ip_address VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    ae_title VARCHAR(64),
    base_url TEXT,
    username VARCHAR(150),
    password VARCHAR(255),
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT REFERENCES users(id),
    modified_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_hds_port_positive CHECK (port > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_hospital_dicom_servers_hospital_name_active
    ON hospital_dicom_servers (hospital_id, LOWER(name))
    WHERE is_active = 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hospital_dicom_servers_hospital_endpoint_active
    ON hospital_dicom_servers (hospital_id, LOWER(ip_address), port, LOWER(COALESCE(ae_title, '')))
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_hospital_dicom_servers_hospital_active
    ON hospital_dicom_servers (hospital_id, is_active, id DESC);

CREATE TABLE IF NOT EXISTS hospital_modality_server_routes (
    id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    modality_id BIGINT NOT NULL,
    dicom_server_id BIGINT NOT NULL REFERENCES hospital_dicom_servers(id),
    priority INTEGER NOT NULL DEFAULT 100,
    weight INTEGER NOT NULL DEFAULT 100,
    is_default SMALLINT NOT NULL DEFAULT 2,
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT REFERENCES users(id),
    modified_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_hmsr_hospital_modality FOREIGN KEY (hospital_id, modality_id)
        REFERENCES hospital_modalities(hospital_id, modality_id),
    CONSTRAINT ux_hmsr_unique UNIQUE (hospital_id, modality_id, dicom_server_id),
    CONSTRAINT chk_hmsr_priority_positive CHECK (priority > 0),
    CONSTRAINT chk_hmsr_weight_positive CHECK (weight > 0),
    CONSTRAINT chk_hmsr_is_default CHECK (is_default IN (1, 2))
);

CREATE INDEX IF NOT EXISTS idx_hmsr_hospital_modality_active
    ON hospital_modality_server_routes (hospital_id, modality_id, is_active, priority, id);

CREATE INDEX IF NOT EXISTS idx_hmsr_server_active
    ON hospital_modality_server_routes (dicom_server_id, is_active, hospital_id, modality_id);

ALTER TABLE pacs_patient_queue
    ADD COLUMN IF NOT EXISTS dicom_server_id BIGINT REFERENCES hospital_dicom_servers(id);

CREATE INDEX IF NOT EXISTS idx_pacs_patient_queue_hospital_dicom_server
    ON pacs_patient_queue (hospital_id, dicom_server_id, id DESC);
