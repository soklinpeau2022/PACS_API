CREATE TABLE IF NOT EXISTS hospital_modality_services (
    id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    modality_id BIGINT NOT NULL REFERENCES modalities(id),
    service_id BIGINT NOT NULL REFERENCES services(id),
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT NULL REFERENCES users(id),
    modified_by BIGINT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ NULL DEFAULT NOW(),
    CONSTRAINT ux_hospital_modality_services UNIQUE (hospital_id, modality_id, service_id)
);

CREATE INDEX IF NOT EXISTS idx_hms_hospital_modality_active
    ON hospital_modality_services (hospital_id, modality_id, is_active, service_id);

CREATE INDEX IF NOT EXISTS idx_hms_service_active
    ON hospital_modality_services (service_id, is_active, hospital_id, modality_id);

-- Bootstrap mapping from existing queue history to avoid breaking current environments.
INSERT INTO hospital_modality_services (
    hospital_id,
    modality_id,
    service_id,
    is_active,
    created_by,
    modified_by,
    created_at,
    modified_at
)
SELECT DISTINCT
    q.hospital_id,
    q.modality_id,
    q.service_id,
    1,
    q.created_by,
    q.modified_by,
    NOW(),
    NOW()
FROM pacs_patient_queue q
INNER JOIN hospital_modalities hm
    ON hm.hospital_id = q.hospital_id
   AND hm.modality_id = q.modality_id
   AND hm.is_active = 1
INNER JOIN services s
    ON s.id = q.service_id
   AND s.is_active = 1
INNER JOIN modalities m
    ON m.id = q.modality_id
   AND m.is_active = 1
WHERE q.service_id IS NOT NULL
  AND q.modality_id IS NOT NULL
ON CONFLICT (hospital_id, modality_id, service_id) DO UPDATE
SET
    is_active = 1,
    modified_by = EXCLUDED.modified_by,
    modified_at = NOW();

