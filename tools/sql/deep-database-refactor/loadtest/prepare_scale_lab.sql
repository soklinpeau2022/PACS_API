\set ON_ERROR_STOP on
\pset pager off

\if :{?row_count}
\else
\set row_count 1000000
\endif

DO $$
BEGIN
    IF current_database() !~* '(load|perf|bench|migration_test)' THEN
        RAISE EXCEPTION
            'Refusing scale generation in database %. Use a disposable load/perf database.',
            current_database();
    END IF;
END;
$$;

DROP SCHEMA IF EXISTS pacs_scale_lab CASCADE;
CREATE SCHEMA pacs_scale_lab;

CREATE UNLOGGED TABLE pacs_scale_lab.studies (
    id BIGINT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    modality_id BIGINT NOT NULL,
    status SMALLINT NOT NULL,
    study_instance_uid TEXT NOT NULL,
    accession_number TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    is_active SMALLINT NOT NULL DEFAULT 1
);

INSERT INTO pacs_scale_lab.studies (
    id,
    hospital_id,
    patient_id,
    modality_id,
    status,
    study_instance_uid,
    accession_number,
    received_at,
    is_active
)
SELECT
    value,
    1 + (value % 20),
    1 + (value % 1000000),
    1 + (value % 12),
    1 + (value % 2),
    '1.2.826.0.1.3680043.10.' || value,
    'ACC-' || LPAD(value::text, 12, '0'),
    NOW() - ((value % 31536000) || ' seconds')::interval,
    1
FROM generate_series(1, :row_count) AS value;

CREATE UNIQUE INDEX ux_scale_studies_hospital_uid
    ON pacs_scale_lab.studies (hospital_id, study_instance_uid);
CREATE INDEX idx_scale_studies_hospital_received_id
    ON pacs_scale_lab.studies (hospital_id, received_at DESC, id DESC)
    WHERE is_active = 1;
CREATE INDEX idx_scale_studies_hospital_patient_received_id
    ON pacs_scale_lab.studies (hospital_id, patient_id, received_at DESC, id DESC)
    WHERE is_active = 1;
CREATE INDEX idx_scale_studies_hospital_modality_status_received_id
    ON pacs_scale_lab.studies (hospital_id, modality_id, status, received_at DESC, id DESC)
    WHERE is_active = 1;

ANALYZE pacs_scale_lab.studies;

-- ---------------------------------------------------------------------------
-- Additional high-growth tables for a fuller large-scale benchmark.
-- Parameterized; all default to the documented targets. Override per run, e.g.
--   psql ... -v row_count=10000000 -v patient_count=1000000 -v worklist_count=5000000 -v log_count=10000000
-- ---------------------------------------------------------------------------
\if :{?patient_count}
\else
\set patient_count 1000000
\endif
\if :{?worklist_count}
\else
\set worklist_count 5000000
\endif
\if :{?log_count}
\else
\set log_count 10000000
\endif
\if :{?result_count}
\else
\set result_count 500000
\endif
\if :{?event_count}
\else
\set event_count :log_count
\endif

-- patients: mirrors the V191 index set (hospital_id, is_active, id DESC) + name/phone search
CREATE UNLOGGED TABLE pacs_scale_lab.patients (
    id BIGINT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_uid TEXT NOT NULL,
    first_name TEXT,
    last_name TEXT,
    phone_number TEXT,
    is_active SMALLINT NOT NULL DEFAULT 1
);
INSERT INTO pacs_scale_lab.patients (id, hospital_id, patient_uid, first_name, last_name, phone_number, is_active)
SELECT value,
       1 + (value % 20),
       'PID-' || LPAD(value::text, 12, '0'),
       'First' || (value % 100000),
       'Last' || (value % 50000),
       '0' || LPAD(((value % 900000000) + 100000000)::text, 9, '0'),
       1
FROM generate_series(1, :patient_count) AS value;
CREATE UNIQUE INDEX ux_scale_patients_hospital_uid_lower
    ON pacs_scale_lab.patients (hospital_id, LOWER(patient_uid));
CREATE INDEX idx_scale_patients_hospital_active_id
    ON pacs_scale_lab.patients (hospital_id, is_active, id DESC);
CREATE INDEX idx_scale_patients_hospital_name
    ON pacs_scale_lab.patients (hospital_id, LOWER(first_name), LOWER(last_name), id DESC);
CREATE INDEX idx_scale_patients_hospital_phone
    ON pacs_scale_lab.patients (hospital_id, LOWER(phone_number))
    WHERE is_active = 1 AND phone_number IS NOT NULL;
CREATE INDEX gin_scale_patients_uid_trgm
    ON pacs_scale_lab.patients USING gin (patient_uid gin_trgm_ops);
ANALYZE pacs_scale_lab.patients;

-- worklists: mirrors the keyset list index (hospital_id, status, created_at DESC, id DESC) and the
-- active-queue partial index used by the dashboard snapshot.
CREATE UNLOGGED TABLE pacs_scale_lab.worklists (
    id BIGINT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    modality_id BIGINT NOT NULL,
    status SMALLINT NOT NULL,
    study_id BIGINT,
    image_received_at TIMESTAMPTZ,
    scheduled_date DATE,
    created_at TIMESTAMPTZ NOT NULL
);
INSERT INTO pacs_scale_lab.worklists (id, hospital_id, patient_id, modality_id, status, study_id, image_received_at, scheduled_date, created_at)
SELECT value,
       1 + (value % 20),
       1 + (value % GREATEST(1, :patient_count)),
       1 + (value % 12),
       1 + (value % 4),
       CASE WHEN value % 3 = 0 THEN value ELSE NULL END,
       CASE WHEN value % 3 = 0 THEN NOW() - ((value % 31536000) || ' seconds')::interval ELSE NULL END,
       (NOW() - ((value % 31536000) || ' seconds')::interval)::date,
       NOW() - ((value % 31536000) || ' seconds')::interval
FROM generate_series(1, :worklist_count) AS value;
CREATE INDEX idx_scale_worklists_hospital_status_created
    ON pacs_scale_lab.worklists (hospital_id, status, created_at DESC, id DESC);
CREATE INDEX idx_scale_worklists_active_hospital_status_schedule_id
    ON pacs_scale_lab.worklists (hospital_id, status, scheduled_date, id DESC)
    WHERE status = ANY (ARRAY[1, 2, 3, 4]) AND study_id IS NULL AND image_received_at IS NULL;
ANALYZE pacs_scale_lab.worklists;

-- results and images: mirrors patient/result list and image-by-result detail paths.
CREATE UNLOGGED TABLE pacs_scale_lab.results (
    id BIGINT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    study_id BIGINT,
    worklist_id BIGINT,
    status TEXT NOT NULL,
    is_active SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL
);
INSERT INTO pacs_scale_lab.results (id, hospital_id, patient_id, study_id, worklist_id, status, is_active, created_at)
SELECT value,
       1 + (value % 20),
       1 + (value % GREATEST(1, :patient_count)),
       1 + (value % GREATEST(1, :row_count)),
       1 + (value % GREATEST(1, :worklist_count)),
       CASE value % 5
           WHEN 0 THEN 'IMAGE_RECEIVED'
           WHEN 1 THEN 'DRAFT'
           WHEN 2 THEN 'PRELIMINARY'
           WHEN 3 THEN 'FINAL'
           ELSE 'CANCELLED'
       END,
       1,
       NOW() - ((value % 31536000) || ' seconds')::interval
FROM generate_series(1, :result_count) AS value;
CREATE INDEX idx_scale_results_hospital_patient_created
    ON pacs_scale_lab.results (hospital_id, patient_id, created_at DESC, id DESC)
    WHERE is_active = 1;
CREATE INDEX idx_scale_results_hospital_status_created
    ON pacs_scale_lab.results (hospital_id, status, created_at DESC, id DESC)
    WHERE is_active = 1;
ANALYZE pacs_scale_lab.results;

CREATE UNLOGGED TABLE pacs_scale_lab.result_images (
    id BIGINT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    result_id BIGINT NOT NULL,
    image_path TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    is_active SMALLINT NOT NULL DEFAULT 1
);
INSERT INTO pacs_scale_lab.result_images (id, hospital_id, result_id, image_path, sort_order, is_active)
SELECT value,
       1 + (value % 20),
       1 + (value % GREATEST(1, :result_count)),
       'results/' || (1 + (value % GREATEST(1, :result_count))) || '/' || value || '.jpg',
       value % 20,
       1
FROM generate_series(1, :result_count) AS value;
CREATE INDEX idx_scale_result_images_hospital_result_active
    ON pacs_scale_lab.result_images (hospital_id, result_id, is_active, sort_order, id);
ANALYZE pacs_scale_lab.result_images;

-- durable realtime notification events and dashboard summaries.
CREATE UNLOGGED TABLE pacs_scale_lab.notification_events (
    id BIGINT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    dedupe_key TEXT NOT NULL,
    event_type TEXT NOT NULL,
    title TEXT,
    message TEXT,
    worklist_id BIGINT,
    study_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);
INSERT INTO pacs_scale_lab.notification_events (
    id,
    hospital_id,
    dedupe_key,
    event_type,
    title,
    message,
    worklist_id,
    study_id,
    created_at
)
SELECT value,
       1 + (value % 20),
       'event-' || value,
       CASE value % 3 WHEN 0 THEN 'WORKLIST' WHEN 1 THEN 'STUDY' ELSE 'RESULT' END,
       'Event ' || value,
       'Synthetic event ' || value,
       1 + (value % GREATEST(1, :worklist_count)),
       1 + (value % GREATEST(1, :row_count)),
       NOW() - ((value % 31536000) || ' seconds')::interval
FROM generate_series(1, :event_count) AS value;
CREATE UNIQUE INDEX ux_scale_notification_events_hospital_dedupe
    ON pacs_scale_lab.notification_events (hospital_id, dedupe_key);
CREATE INDEX idx_scale_notification_events_hospital_cursor
    ON pacs_scale_lab.notification_events (hospital_id, id);
CREATE INDEX idx_scale_notification_events_hospital_created
    ON pacs_scale_lab.notification_events (hospital_id, created_at DESC, id DESC);
ANALYZE pacs_scale_lab.notification_events;

CREATE UNLOGGED TABLE pacs_scale_lab.callback_log (
    id BIGINT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    dicom_server_id BIGINT,
    dedupe_key TEXT,
    payload_sha256 CHAR(64),
    event TEXT,
    accession_number TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    success BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO pacs_scale_lab.callback_log (
    id,
    hospital_id,
    dicom_server_id,
    dedupe_key,
    payload_sha256,
    event,
    accession_number,
    received_at,
    attempt_count,
    success
)
SELECT value,
       1 + (value % 20),
       1 + (value % 20),
       'callback-' || value,
       repeat(to_hex(value % 16), 64),
       'STUDY_RECEIVED',
       'ACC-' || LPAD(value::text, 12, '0'),
       NOW() - ((value % 31536000) || ' seconds')::interval,
       1,
       value % 2 = 0
FROM generate_series(1, :event_count) AS value;
CREATE UNIQUE INDEX ux_scale_callback_log_hospital_dedupe
    ON pacs_scale_lab.callback_log (hospital_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_scale_callback_log_hospital_received
    ON pacs_scale_lab.callback_log (hospital_id, received_at DESC, id DESC);
CREATE INDEX idx_scale_callback_log_hospital_server_received
    ON pacs_scale_lab.callback_log (hospital_id, dicom_server_id, received_at DESC, id DESC)
    WHERE dicom_server_id IS NOT NULL;
CREATE INDEX idx_scale_callback_log_hospital_accession
    ON pacs_scale_lab.callback_log (hospital_id, accession_number)
    WHERE accession_number IS NOT NULL;
ANALYZE pacs_scale_lab.callback_log;

CREATE UNLOGGED TABLE pacs_scale_lab.daily_stats (
    hospital_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    modality_id BIGINT NOT NULL DEFAULT 0,
    waiting_count BIGINT NOT NULL,
    in_progress_count BIGINT NOT NULL,
    cancelled_count BIGINT NOT NULL,
    failed_count BIGINT NOT NULL,
    received_study_count BIGINT NOT NULL,
    completed_result_count BIGINT NOT NULL,
    PRIMARY KEY (hospital_id, stat_date, modality_id)
);
INSERT INTO pacs_scale_lab.daily_stats (
    hospital_id,
    stat_date,
    modality_id,
    waiting_count,
    in_progress_count,
    cancelled_count,
    failed_count,
    received_study_count,
    completed_result_count
)
SELECT hospital_id,
       stat_date::date,
       modality_id,
       (hospital_id * modality_id * 3) % 1000,
       (hospital_id * modality_id * 5) % 1000,
       (hospital_id * modality_id * 7) % 1000,
       (hospital_id * modality_id * 11) % 1000,
       (hospital_id * modality_id * 13) % 1000,
       (hospital_id * modality_id * 17) % 1000
FROM generate_series(1, 20) AS hospital_id
CROSS JOIN generate_series(current_date - INTERVAL '400 days', current_date, INTERVAL '1 day') AS stat_date
CROSS JOIN generate_series(0, 12) AS modality_id;
ANALYZE pacs_scale_lab.daily_stats;

-- Partitioned activity log to benchmark partition pruning (mirrors V192 design).
CREATE TABLE pacs_scale_lab.activity_log (
    id BIGINT NOT NULL,
    hospital_id BIGINT,
    action TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
DO $$
DECLARE m date;
BEGIN
    FOR m IN SELECT generate_series(date_trunc('month', now()) - interval '11 months',
                                    date_trunc('month', now()) + interval '1 month',
                                    interval '1 month')::date
    LOOP
        EXECUTE format(
            'CREATE UNLOGGED TABLE pacs_scale_lab.activity_log_%s PARTITION OF pacs_scale_lab.activity_log FOR VALUES FROM (%L) TO (%L)',
            to_char(m, 'YYYYMM'), m, (m + interval '1 month')::date);
    END LOOP;
    EXECUTE 'CREATE UNLOGGED TABLE pacs_scale_lab.activity_log_default PARTITION OF pacs_scale_lab.activity_log DEFAULT';
END;
$$;
INSERT INTO pacs_scale_lab.activity_log (id, hospital_id, action, created_at)
SELECT value,
       1 + (value % 20),
       'action-' || (value % 50),
       NOW() - ((value % 31104000) || ' seconds')::interval   -- spread over ~12 months
FROM generate_series(1, :log_count) AS value;
CREATE INDEX idx_scale_activity_created_id
    ON pacs_scale_lab.activity_log (created_at DESC, id DESC);
ANALYZE pacs_scale_lab.activity_log;
