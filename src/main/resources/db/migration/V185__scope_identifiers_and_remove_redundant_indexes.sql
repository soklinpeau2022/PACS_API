-- This migration is intentionally non-transactional because PostgreSQL requires
-- CREATE/DROP INDEX CONCURRENTLY to run outside a transaction.
--
-- Precondition:
--   Run tools/sql/large-scale-schema-audit/validate_pre_migration.sql.
--
-- Rollback:
--   See tools/sql/large-scale-schema-audit/rollback_v185.sql.
--   Reintroducing global uniqueness can fail if two hospitals have since reused
--   the same patient UID or visit code.

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_patients_hospital_patient_uid_lower
    ON patients (hospital_id, LOWER(patient_uid));

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_pacs_worklists_hospital_visit_code_lower
    ON pacs_worklists (hospital_id, LOWER(visit_code))
    WHERE visit_code IS NOT NULL
      AND BTRIM(visit_code) <> '';

-- Retain efficient administrator/legacy callback lookup without enforcing
-- cross-hospital uniqueness.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pacs_worklists_lower_visit_code
    ON pacs_worklists (LOWER(visit_code))
    WHERE visit_code IS NOT NULL
      AND BTRIM(visit_code) <> '';

DROP INDEX CONCURRENTLY IF EXISTS ux_patients_patient_uid_global_lower;
DROP INDEX CONCURRENTLY IF EXISTS ux_pacs_worklists_visit_code_global;

-- Exact duplicates or indexes fully covered by a primary/unique index.
DROP INDEX CONCURRENTLY IF EXISTS idx_refresh_tokens_token_hash;
DROP INDEX CONCURRENTLY IF EXISTS idx_revoked_tokens_jti;
DROP INDEX CONCURRENTLY IF EXISTS idx_role_module_details_role_module;
DROP INDEX CONCURRENTLY IF EXISTS idx_role_module_details_role_id;
DROP INDEX CONCURRENTLY IF EXISTS idx_role_module_details_module_detail_id;
DROP INDEX CONCURRENTLY IF EXISTS idx_countries_name;
DROP INDEX CONCURRENTLY IF EXISTS idx_oauth2_clients_client_id;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_patient_sequences_hospital_year;
DROP INDEX CONCURRENTLY IF EXISTS idx_hds_hospital_active_id_desc;

-- Covered by the hospital-scoped unique indexes above.
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_lower_uid_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_hospital_lower_visit_code;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_visit_code;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_study_uid;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_study_uid_active;

-- Covered by wider indexes matching the actual mapper predicates/order.
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_hospital_status_lookup;
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_viewer_states_accession;
DROP INDEX CONCURRENTLY IF EXISTS idx_system_activities_created;
DROP INDEX CONCURRENTLY IF EXISTS idx_system_activities_created_by;
DROP INDEX CONCURRENTLY IF EXISTS idx_system_activities_module_id;
DROP INDEX CONCURRENTLY IF EXISTS idx_system_activities_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_system_activities_endpoint;
