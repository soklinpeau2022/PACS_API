\set ON_ERROR_STOP on

-- Compatibility rollback. New columns/tables are deliberately retained so
-- captured audit/history data is never destroyed. Reverting application code
-- is safe after restoring the older indexes and single-column FKs below.

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_worklists_hospital_visit_code
    ON pacs_worklists (hospital_id, visit_code)
    WHERE visit_code IS NOT NULL;

ALTER TABLE patients
    ADD CONSTRAINT patients_hospital_id_patient_uid_key
    UNIQUE (hospital_id, patient_uid);

ALTER TABLE pacs_worklists
    DROP CONSTRAINT IF EXISTS fk_worklists_study_hospital;
ALTER TABLE pacs_worklist_study_links
    DROP CONSTRAINT IF EXISTS fk_worklist_study_links_worklist_hospital,
    DROP CONSTRAINT IF EXISTS fk_worklist_study_links_study_hospital;
ALTER TABLE pacs_results
    DROP CONSTRAINT IF EXISTS fk_results_study_hospital,
    DROP CONSTRAINT IF EXISTS fk_results_worklist_hospital,
    DROP CONSTRAINT IF EXISTS fk_results_patient_hospital;
ALTER TABLE pacs_result_images
    DROP CONSTRAINT IF EXISTS fk_result_images_result_hospital,
    DROP CONSTRAINT IF EXISTS fk_result_images_result_restrict;
ALTER TABLE pacs_viewer_states
    DROP CONSTRAINT IF EXISTS fk_viewer_states_study_hospital,
    DROP CONSTRAINT IF EXISTS fk_viewer_states_worklist_hospital,
    DROP CONSTRAINT IF EXISTS fk_viewer_states_patient_hospital;
ALTER TABLE pacs_realtime_notification_events
    DROP CONSTRAINT IF EXISTS fk_realtime_events_hospital_restrict,
    DROP CONSTRAINT IF EXISTS fk_realtime_events_worklist_hospital,
    DROP CONSTRAINT IF EXISTS fk_realtime_events_study_hospital;

ALTER TABLE pacs_worklists
    ADD CONSTRAINT fk_pacs_worklists_study_id
    FOREIGN KEY (study_id) REFERENCES pacs_studies(id)
    ON UPDATE RESTRICT ON DELETE SET NULL NOT VALID;
ALTER TABLE pacs_worklist_study_links
    ADD CONSTRAINT pacs_worklist_study_links_worklist_id_fkey
    FOREIGN KEY (worklist_id) REFERENCES pacs_worklists(id) ON DELETE CASCADE NOT VALID,
    ADD CONSTRAINT pacs_worklist_study_links_study_id_fkey
    FOREIGN KEY (study_id) REFERENCES pacs_studies(id) ON DELETE CASCADE NOT VALID;
ALTER TABLE pacs_results
    ADD CONSTRAINT pacs_results_study_id_fkey
    FOREIGN KEY (study_id) REFERENCES pacs_studies(id) NOT VALID,
    ADD CONSTRAINT pacs_results_worklist_id_fkey
    FOREIGN KEY (worklist_id) REFERENCES pacs_worklists(id) NOT VALID,
    ADD CONSTRAINT pacs_results_patient_id_fkey
    FOREIGN KEY (patient_id) REFERENCES patients(id) NOT VALID;
ALTER TABLE pacs_result_images
    ADD CONSTRAINT pacs_result_images_result_id_fkey
    FOREIGN KEY (result_id) REFERENCES pacs_results(id) ON DELETE CASCADE NOT VALID;

