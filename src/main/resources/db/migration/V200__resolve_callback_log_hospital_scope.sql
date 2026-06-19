-- V200: Promote safely resolvable callback quarantine rows back to the
-- hospital-scoped callback log.
--
-- Rows are promoted only when their accession-like value resolves to exactly
-- one hospital through PACS studies or worklists. Ambiguous or still-unmatched
-- rows remain in dicom_server_unmatched_callback_log for audit review.

WITH accession_matches AS (
    SELECT
        u.id AS unmatched_id,
        w.hospital_id
    FROM public.dicom_server_unmatched_callback_log u
    JOIN public.pacs_worklists w
      ON LOWER(w.visit_code) = LOWER(u.accession_number)
    WHERE u.accession_number IS NOT NULL
      AND BTRIM(u.accession_number) <> ''

    UNION ALL

    SELECT
        u.id AS unmatched_id,
        s.hospital_id
    FROM public.dicom_server_unmatched_callback_log u
    JOIN public.pacs_studies s
      ON LOWER(s.accession_number) = LOWER(u.accession_number)
    WHERE u.accession_number IS NOT NULL
      AND BTRIM(u.accession_number) <> ''

    UNION ALL

    SELECT
        u.id AS unmatched_id,
        s.hospital_id
    FROM public.dicom_server_unmatched_callback_log u
    JOIN public.pacs_studies s
      ON LOWER(s.reference_visit_code) = LOWER(u.accession_number)
    WHERE u.accession_number IS NOT NULL
      AND BTRIM(u.accession_number) <> ''
),
resolved AS (
    SELECT
        u.id AS unmatched_id,
        COUNT(DISTINCT m.hospital_id) FILTER (WHERE m.hospital_id IS NOT NULL) AS hospital_count,
        MIN(m.hospital_id) FILTER (WHERE m.hospital_id IS NOT NULL) AS hospital_id
    FROM public.dicom_server_unmatched_callback_log u
    LEFT JOIN accession_matches m ON m.unmatched_id = u.id
    GROUP BY u.id
),
promotable AS (
    SELECT
        u.id AS unmatched_id,
        COALESCE(u.original_callback_log_id, u.id) AS callback_log_id,
        u.event,
        u.payload,
        u.success,
        u.error_message,
        u.warning_message,
        u.received_at,
        u.created_at,
        u.accession_number,
        u.dicom_server_study_id,
        u.dicom_server_patient_id,
        u.dicom_server_series_ids,
        r.hospital_id,
        u.dicom_server_id,
        u.dedupe_key,
        u.payload_sha256,
        u.attempt_count,
        u.last_received_at
    FROM public.dicom_server_unmatched_callback_log u
    JOIN resolved r ON r.unmatched_id = u.id
    WHERE r.hospital_count = 1
),
inserted AS (
    INSERT INTO public.dicom_server_callback_log (
        id,
        event,
        payload,
        success,
        error_message,
        warning_message,
        received_at,
        created_at,
        accession_number,
        dicom_server_study_id,
        dicom_server_patient_id,
        dicom_server_series_ids,
        hospital_id,
        dicom_server_id,
        dedupe_key,
        payload_sha256,
        attempt_count,
        last_received_at
    )
    SELECT
        p.callback_log_id,
        p.event,
        p.payload,
        p.success,
        p.error_message,
        p.warning_message,
        p.received_at,
        p.created_at,
        p.accession_number,
        p.dicom_server_study_id,
        p.dicom_server_patient_id,
        p.dicom_server_series_ids,
        p.hospital_id,
        p.dicom_server_id,
        p.dedupe_key,
        p.payload_sha256,
        p.attempt_count,
        COALESCE(p.last_received_at, p.received_at)
    FROM promotable p
    WHERE NOT EXISTS (
        SELECT 1
        FROM public.dicom_server_callback_log existing
        WHERE existing.id = p.callback_log_id
           OR (
                p.dedupe_key IS NOT NULL
            AND existing.hospital_id = p.hospital_id
            AND existing.dedupe_key = p.dedupe_key
           )
    )
    RETURNING id, received_at, hospital_id, dedupe_key
)
DELETE FROM public.dicom_server_unmatched_callback_log u
USING promotable p
JOIN inserted i
  ON i.id = p.callback_log_id
 AND i.received_at = p.received_at
 AND i.hospital_id = p.hospital_id
 AND (
      p.dedupe_key IS NULL
      OR i.dedupe_key = p.dedupe_key
 )
WHERE u.id = p.unmatched_id
;

SELECT SETVAL(
    'public.dicom_server_callback_log_id_seq',
    GREATEST(
        COALESCE((SELECT MAX(id) FROM public.dicom_server_callback_log), 0),
        COALESCE((SELECT last_value FROM public.dicom_server_callback_log_id_seq), 1)
    ),
    TRUE
);

ALTER TABLE public.dicom_server_callback_log
    VALIDATE CONSTRAINT fk_callback_log_hospital;

ALTER TABLE public.dicom_server_callback_log
    VALIDATE CONSTRAINT fk_callback_log_server_hospital;
