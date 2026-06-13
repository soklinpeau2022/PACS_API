-- PACS relationship and integrity hardening for large-scale workloads.
-- Safe for pre-production bootstrap and new environments (project currently has no data).

-- 1) Normalize core timestamps for deterministic sorting.
UPDATE pacs_studies
SET created = COALESCE(created, NOW())
WHERE created IS NULL;

UPDATE pacs_queue_results
SET created_at = COALESCE(created_at, created, NOW()),
    modified_at = COALESCE(modified_at, modified, NOW())
WHERE created_at IS NULL OR modified_at IS NULL;

UPDATE pacs_patient_queue
SET modified_at = COALESCE(modified_at, created_at, created, NOW())
WHERE modified_at IS NULL;

-- 2) Explicit data-state checks.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_patients_is_active') THEN
        ALTER TABLE patients
            ADD CONSTRAINT chk_patients_is_active CHECK (is_active IN (1, 2));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_studies_is_active') THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT chk_pacs_studies_is_active CHECK (is_active IN (1, 2));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_queue_results_is_active') THEN
        ALTER TABLE pacs_queue_results
            ADD CONSTRAINT chk_pacs_queue_results_is_active CHECK (is_active IN (1, 2));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_patient_queue_status') THEN
        ALTER TABLE pacs_patient_queue
            ADD CONSTRAINT chk_pacs_patient_queue_status
                CHECK (status IN (
                    'WAITING', 'SENDING', 'SENT_TO_PACS', 'RECEIVED',
                    'TRANSLATED', 'REPORTED', 'IN_PROGRESS', 'COMPLETED',
                    'CANCELLED', 'RETURNED', 'NO_SHOW'
                ));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_studies_status') THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT chk_pacs_studies_status
                CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));
    END IF;
END $$;

-- 3) Cross-table relationship consistency (hospital scoped).
-- Add a unique key to support composite foreign keys by (id, hospital_id).
CREATE UNIQUE INDEX IF NOT EXISTS ux_queue_id_hospital
    ON pacs_patient_queue (id, hospital_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_patients_id_hospital
    ON patients (id, hospital_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_queue_patient_hospital') THEN
        ALTER TABLE pacs_patient_queue
            ADD CONSTRAINT fk_queue_patient_hospital
                FOREIGN KEY (patient_id, hospital_id)
                REFERENCES patients (id, hospital_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_queue_results_queue_hospital') THEN
        ALTER TABLE pacs_queue_results
            ADD CONSTRAINT fk_queue_results_queue_hospital
                FOREIGN KEY (queue_id, hospital_id)
                REFERENCES pacs_patient_queue (id, hospital_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_queue_histories_patient_hospital') THEN
        ALTER TABLE pacs_patient_queue_histories
            ADD CONSTRAINT fk_queue_histories_patient_hospital
                FOREIGN KEY (patient_id, hospital_id)
                REFERENCES patients (id, hospital_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_queue_histories_queue_hospital') THEN
        ALTER TABLE pacs_patient_queue_histories
            ADD CONSTRAINT fk_queue_histories_queue_hospital
                FOREIGN KEY (queue_id, hospital_id)
                REFERENCES pacs_patient_queue (id, hospital_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_studies_patient_hospital') THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT fk_studies_patient_hospital
                FOREIGN KEY (patient_id, hospital_id)
                REFERENCES patients (id, hospital_id)
                ON UPDATE RESTRICT
                ON DELETE RESTRICT;
    END IF;
END $$;

-- 4) Big-data query/index coverage for hospital-scoped joins and sorting.
CREATE INDEX IF NOT EXISTS idx_patients_hospital_id_desc
    ON patients (hospital_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_studies_hospital_status_id_desc
    ON pacs_studies (hospital_id, status, id DESC);

CREATE INDEX IF NOT EXISTS idx_queue_results_hospital_queue_id_desc
    ON pacs_queue_results (hospital_id, queue_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_queue_histories_hospital_queue_id_desc
    ON pacs_patient_queue_histories (hospital_id, queue_id, id DESC);
