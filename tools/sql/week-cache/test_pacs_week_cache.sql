\set ON_ERROR_STOP on
\pset pager off

-- Transactional cache consistency test. It uses one existing patient scope,
-- verifies trigger/refresh/cleanup behavior, and rolls back all test rows.

BEGIN;

DO $$
DECLARE
    scope_row RECORD;
    worklist_id BIGINT;
    study_id BIGINT;
    test_suffix TEXT := TXID_CURRENT()::TEXT;
BEGIN
    SELECT
        patient.hospital_id,
        patient.id AS patient_id,
        modality.id AS modality_id
    INTO scope_row
    FROM patients patient
    LEFT JOIN LATERAL (
        SELECT id
        FROM modalities
        WHERE is_active = 1
        ORDER BY id
        LIMIT 1
    ) modality ON TRUE
    ORDER BY patient.id
    LIMIT 1;

    IF scope_row.patient_id IS NULL THEN
        RAISE EXCEPTION 'week-cache test requires at least one patient';
    END IF;

    INSERT INTO pacs_worklists (
        hospital_id,
        patient_id,
        modality_id,
        visit_code,
        status,
        study_description,
        created,
        created_at,
        modified_at
    )
    VALUES (
        scope_row.hospital_id,
        scope_row.patient_id,
        scope_row.modality_id,
        'CACHE-TEST-' || test_suffix,
        1,
        'Weekly cache transaction test',
        NOW(),
        NOW(),
        NOW()
    )
    RETURNING id INTO worklist_id;

    IF NOT EXISTS (
        SELECT 1
        FROM pacs_worklists_week_cache
        WHERE id = worklist_id
    ) THEN
        RAISE EXCEPTION 'recent Worklist was not synchronized to cache';
    END IF;

    UPDATE pacs_worklists
    SET status = 3,
        cancelled_at = NOW(),
        modified_at = NOW()
    WHERE id = worklist_id;

    IF (SELECT status FROM pacs_worklists_week_cache WHERE id = worklist_id) <> 3 THEN
        RAISE EXCEPTION 'Worklist cancel/status update was not synchronized';
    END IF;

    UPDATE pacs_worklists
    SET created = NOW() - INTERVAL '8 days',
        created_at = NOW() - INTERVAL '8 days',
        modified_at = NOW()
    WHERE id = worklist_id;

    IF EXISTS (
        SELECT 1
        FROM pacs_worklists_week_cache
        WHERE id = worklist_id
    ) THEN
        RAISE EXCEPTION 'old Worklist remained in cache after trigger sync';
    END IF;

    INSERT INTO pacs_worklists_week_cache (
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        visit_code,
        status,
        created_at,
        cached_at
    )
    SELECT
        id,
        public_id,
        hospital_id,
        patient_id,
        modality_id,
        visit_code,
        status,
        created_at,
        NOW()
    FROM pacs_worklists
    WHERE id = worklist_id;

    PERFORM cleanup_pacs_week_cache();

    IF EXISTS (
        SELECT 1
        FROM pacs_worklists_week_cache
        WHERE id = worklist_id
    ) THEN
        RAISE EXCEPTION 'cleanup did not remove old Worklist cache row';
    END IF;

    INSERT INTO pacs_studies (
        hospital_id,
        patient_id,
        modality_id,
        study_instance_uid,
        accession_number,
        status,
        is_active,
        received_at,
        image_received_at,
        created,
        created_at,
        modified
    )
    VALUES (
        scope_row.hospital_id,
        scope_row.patient_id,
        scope_row.modality_id,
        '1.2.826.0.1.3680043.10.999.' || test_suffix,
        'CACHE-STUDY-' || test_suffix,
        1,
        1,
        NOW(),
        NOW(),
        NOW(),
        NOW(),
        NOW()
    )
    RETURNING id INTO study_id;

    IF NOT EXISTS (
        SELECT 1
        FROM pacs_studies_week_cache
        WHERE id = study_id
    ) THEN
        RAISE EXCEPTION 'recent Study was not synchronized to cache';
    END IF;

    UPDATE pacs_studies
    SET status = 2,
        modified = NOW()
    WHERE id = study_id;

    IF (SELECT status FROM pacs_studies_week_cache WHERE id = study_id) <> 2 THEN
        RAISE EXCEPTION 'Study update was not synchronized';
    END IF;

    UPDATE pacs_studies
    SET is_active = 2,
        modified = NOW()
    WHERE id = study_id;

    IF EXISTS (
        SELECT 1
        FROM pacs_studies_week_cache
        WHERE id = study_id
    ) THEN
        RAISE EXCEPTION 'soft-deleted Study remained in cache';
    END IF;

    UPDATE pacs_worklists
    SET status = 1,
        cancelled_at = NULL,
        created = NOW(),
        created_at = NOW(),
        modified_at = NOW()
    WHERE id = worklist_id;

    UPDATE pacs_studies
    SET status = 1,
        is_active = 1,
        received_at = NOW(),
        image_received_at = NOW(),
        created = NOW(),
        created_at = NOW(),
        modified = NOW()
    WHERE id = study_id;

    PERFORM refresh_pacs_week_cache();

    IF NOT EXISTS (
        SELECT 1
        FROM pacs_worklists_week_cache
        WHERE id = worklist_id
    ) OR NOT EXISTS (
        SELECT 1
        FROM pacs_studies_week_cache
        WHERE id = study_id
    ) THEN
        RAISE EXCEPTION 'full refresh did not preserve source IDs';
    END IF;

    RAISE NOTICE 'weekly cache transaction test passed: Worklist=%, study=%',
        worklist_id, study_id;
END;
$$;

ROLLBACK;
