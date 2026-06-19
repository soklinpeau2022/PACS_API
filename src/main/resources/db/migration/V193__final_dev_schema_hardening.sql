-- Final DEV schema hardening layer for the large-scale PACS cleanup.
--
-- MIGRATION-SAFETY: not-null-backfilled
-- MIGRATION-SAFETY: constraint-guard-reviewed
-- Existing rows are repaired where possible before stricter constraints are
-- added. Newly-added CHECK/FK constraints are NOT VALID unless they are known
-- to be already normalized; new writes are still protected immediately.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Normalize timestamps used by keyset list APIs and future partition pruning.
UPDATE pacs_worklists
SET created_at = COALESCE(created_at, created, NOW())
WHERE created_at IS NULL;

UPDATE pacs_studies
SET
    created_at = COALESCE(created_at, created, NOW()),
    received_at = COALESCE(received_at, image_received_at, created_at, created, NOW())
WHERE created_at IS NULL
   OR received_at IS NULL;

-- Result status lifecycle: old rows used COMPLETED; new PACS result rows use
-- FINAL while study workflow remains IMAGE_RECEIVED/COMPLETED.
UPDATE pacs_results
SET status = 'FINAL'
WHERE status = 'COMPLETED';

-- Backfill denormalized image scope before making hospital_id mandatory.
UPDATE pacs_result_images pi
SET
    hospital_id = COALESCE(pi.hospital_id, pr.hospital_id),
    modality_id = COALESCE(pi.modality_id, pr.modality_id),
    study_id = COALESCE(pi.study_id, pr.study_id),
    worklist_id = COALESCE(pi.worklist_id, pr.worklist_id)
FROM pacs_results pr
WHERE pi.result_id = pr.id
  AND (
      pi.hospital_id IS NULL
      OR pi.modality_id IS NULL
      OR pi.study_id IS NULL
      OR pi.worklist_id IS NULL
  );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pacs_result_images WHERE result_id IS NULL) THEN
        RAISE EXCEPTION 'pacs_result_images.result_id still has NULL values after V193 backfill';
    END IF;
    IF EXISTS (SELECT 1 FROM pacs_result_images WHERE hospital_id IS NULL) THEN
        RAISE EXCEPTION 'pacs_result_images.hospital_id still has NULL values after V193 backfill';
    END IF;

    ALTER TABLE pacs_result_images
        ALTER COLUMN result_id SET NOT NULL,
        ALTER COLUMN hospital_id SET NOT NULL;
END;
$$;

-- Rename legacy composite-key helpers to their current table names when this
-- database has carried the old queue names through the migration chain.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_queue_patient_hospital'
          AND conrelid = 'pacs_worklists'::regclass
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_worklists_patient_hospital'
    ) THEN
        ALTER TABLE pacs_worklists
            RENAME CONSTRAINT fk_queue_patient_hospital TO fk_worklists_patient_hospital;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_studies_patient_hospital'
          AND conrelid = 'pacs_studies'::regclass
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_studies_patient_hospital'
    ) THEN
        ALTER TABLE pacs_studies
            RENAME CONSTRAINT fk_studies_patient_hospital TO fk_pacs_studies_patient_hospital;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_worklists_patient_hospital') THEN
        ALTER TABLE pacs_worklists
            ADD CONSTRAINT fk_worklists_patient_hospital
            FOREIGN KEY (patient_id, hospital_id)
            REFERENCES patients (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_studies_patient_hospital') THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT fk_pacs_studies_patient_hospital
            FOREIGN KEY (patient_id, hospital_id)
            REFERENCES patients (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_result_images_study_hospital') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT fk_result_images_study_hospital
            FOREIGN KEY (study_id, hospital_id)
            REFERENCES pacs_studies (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_result_images_worklist_hospital') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT fk_result_images_worklist_hospital
            FOREIGN KEY (worklist_id, hospital_id)
            REFERENCES pacs_worklists (id, hospital_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT NOT VALID;
    END IF;
END;
$$;

-- Remove redundant simple FKs after the hospital-safe composite FKs exist.
ALTER TABLE pacs_worklists
    DROP CONSTRAINT IF EXISTS pacs_worklists_patient_id_fkey,
    DROP CONSTRAINT IF EXISTS fk_pacs_worklists_dicom_route;

ALTER TABLE pacs_studies
    DROP CONSTRAINT IF EXISTS pacs_studies_patient_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_studies_dicom_server_id_fkey;

ALTER TABLE pacs_results
    DROP CONSTRAINT IF EXISTS pacs_results_study_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_results_worklist_id_fkey,
    DROP CONSTRAINT IF EXISTS pacs_results_patient_id_fkey;

ALTER TABLE pacs_result_images
    DROP CONSTRAINT IF EXISTS pacs_result_images_result_id_fkey,
    DROP CONSTRAINT IF EXISTS fk_result_images_result_restrict;

ALTER TABLE pacs_viewer_states
    DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_study,
    DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_worklist,
    DROP CONSTRAINT IF EXISTS fk_pacs_viewer_states_patient;

-- Domain checks. Existing older equivalent constraints may remain; these names
-- make the final large-scale contract explicit for audits.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_worklists_status_final') THEN
        ALTER TABLE pacs_worklists
            ADD CONSTRAINT chk_pacs_worklists_status_final
            CHECK (status IN (1, 2, 3, 4)) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'pacs_worklists' AND column_name = 'is_active'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_worklists_active_final') THEN
        ALTER TABLE pacs_worklists
            ADD CONSTRAINT chk_pacs_worklists_active_final
            CHECK (is_active IN (1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_studies_active_final') THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT chk_pacs_studies_active_final
            CHECK (is_active IN (1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_studies_status_final') THEN
        ALTER TABLE pacs_studies
            ADD CONSTRAINT chk_pacs_studies_status_final
            CHECK (status IN (1, 2)) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_results_active_final') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT chk_pacs_results_active_final
            CHECK (is_active IN (1, 2)) NOT VALID;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_results_status') THEN
        ALTER TABLE pacs_results DROP CONSTRAINT chk_pacs_results_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_results_status_final') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT chk_pacs_results_status_final
            CHECK (status IN ('IMAGE_RECEIVED', 'DRAFT', 'PRELIMINARY', 'FINAL', 'CANCELLED')) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_results_has_parent') THEN
        ALTER TABLE pacs_results
            ADD CONSTRAINT chk_pacs_results_has_parent
            CHECK (study_id IS NOT NULL OR worklist_id IS NOT NULL) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_pacs_result_images_relative_path') THEN
        ALTER TABLE pacs_result_images
            ADD CONSTRAINT chk_pacs_result_images_relative_path
            CHECK (
                image_path IS NOT NULL
                AND BTRIM(image_path) <> ''
                AND image_path !~* '^[a-z][a-z0-9+.-]*://'
                AND image_path !~ '^//'
                AND image_path !~ '^[A-Za-z]:[\\/]'
            ) NOT VALID;
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_dicom_servers' AND column_name = 'port'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_hds_port_range') THEN
        ALTER TABLE hospital_dicom_servers
            ADD CONSTRAINT chk_hds_port_range
            CHECK (port > 0 AND port <= 65535) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_dicom_servers' AND column_name = 'dicom_port'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_hds_dicom_port_range') THEN
        ALTER TABLE hospital_dicom_servers
            ADD CONSTRAINT chk_hds_dicom_port_range
            CHECK (dicom_port IS NULL OR (dicom_port > 0 AND dicom_port <= 65535)) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_modality_server_routes' AND column_name = 'machine_port'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_hmsr_machine_port_range') THEN
        ALTER TABLE hospital_modality_server_routes
            ADD CONSTRAINT chk_hmsr_machine_port_range
            CHECK (machine_port IS NULL OR (machine_port > 0 AND machine_port <= 65535)) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'hospital_dicom_machines' AND column_name = 'machine_port'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_hdm_machine_port_range') THEN
        ALTER TABLE hospital_dicom_machines
            ADD CONSTRAINT chk_hdm_machine_port_range
            CHECK (machine_port > 0 AND machine_port <= 65535) NOT VALID;
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'study_retention_policies' AND column_name = 'retention_days'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_retention_days_positive') THEN
        ALTER TABLE study_retention_policies
            ADD CONSTRAINT chk_retention_days_positive
            CHECK (retention_days > 0) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'study_retention_policies' AND column_name = 'notify_before_days'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_retention_notify_before_nonnegative') THEN
        ALTER TABLE study_retention_policies
            ADD CONSTRAINT chk_retention_notify_before_nonnegative
            CHECK (notify_before_days >= 0) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'study_retention_policies' AND column_name = 'retention_value'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_retention_value_positive_final') THEN
        ALTER TABLE study_retention_policies
            ADD CONSTRAINT chk_retention_value_positive_final
            CHECK (retention_value > 0) NOT VALID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'study_retention_policies' AND column_name = 'retention_unit'
    ) AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_retention_unit_final') THEN
        ALTER TABLE study_retention_policies
            ADD CONSTRAINT chk_retention_unit_final
            CHECK (retention_unit IN ('DAY', 'MONTH', 'YEAR')) NOT VALID;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_oauth2_clients_access_lifetime_final') THEN
        ALTER TABLE oauth2_clients
            ADD CONSTRAINT chk_oauth2_clients_access_lifetime_final
            CHECK (access_token_lifetime_ms > 0) NOT VALID;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_oauth2_clients_refresh_lifetime_final') THEN
        ALTER TABLE oauth2_clients
            ADD CONSTRAINT chk_oauth2_clients_refresh_lifetime_final
            CHECK (refresh_token_lifetime_ms > 0) NOT VALID;
    END IF;
END;
$$;

COMMENT ON CONSTRAINT chk_pacs_worklists_status_final ON pacs_worklists IS
    'Worklist status map: 1=WAITING, 2=IN_PROGRESS, 3=CANCELLED, 4=FAILED.';

COMMENT ON CONSTRAINT chk_pacs_results_status_final ON pacs_results IS
    'PACS result status values: IMAGE_RECEIVED, DRAFT, PRELIMINARY, FINAL, CANCELLED.';
