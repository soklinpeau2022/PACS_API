CREATE TABLE IF NOT EXISTS modalities (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    is_active   SMALLINT NOT NULL DEFAULT 1,
    created_by  BIGINT,
    modified_by BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_modalities_name_active
    ON modalities (LOWER(name))
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_modalities_active_id_desc
    ON modalities (is_active, id DESC);

CREATE TABLE IF NOT EXISTS hospital_modalities (
    id          BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    modality_id BIGINT NOT NULL REFERENCES modalities(id),
    is_active   SMALLINT NOT NULL DEFAULT 1,
    created_by  BIGINT,
    modified_by BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (hospital_id, modality_id)
);

CREATE INDEX IF NOT EXISTS idx_hospital_modalities_hospital_active
    ON hospital_modalities (hospital_id, is_active, modality_id);

CREATE INDEX IF NOT EXISTS idx_hospital_modalities_modality_active
    ON hospital_modalities (modality_id, is_active, hospital_id);
