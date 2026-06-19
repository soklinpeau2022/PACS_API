-- V191: Drop structurally-redundant indexes on the hottest tables.
--
-- This is intentionally CONSERVATIVE and query-driven. Only indexes that are
-- either a bare single column or a strict left-prefix of a retained wider index
-- whose predicate/order matches the actual MyBatis mapper queries are removed.
-- Overlapping *partial* indexes (the V104/V108/V109 operational indexes that
-- each match a specific mapper predicate) are deliberately KEPT; remaining
-- trim candidates are tracked in docs/database/PERFORMANCE_REFACTOR_V189_PLUS.md
-- for a pg_stat_statements-driven pass once production usage data exists.
--
-- CONCURRENTLY + executeInTransaction=false (see the .conf sidecar) keeps this
-- online and lock-light on large production tables.

-- patients: list always filters is_active = 1 and orders by id DESC, so
-- idx_patients_hospital_active_id_desc (hospital_id, is_active, id DESC) covers
-- these. The bare and strict-prefix variants are redundant.
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital;          -- left-prefix of every (hospital_id, ...) index
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_active;   -- strict prefix of idx_patients_hospital_active_id_desc
DROP INDEX CONCURRENTLY IF EXISTS idx_patients_hospital_id_desc;  -- covered by idx_patients_hospital_active_id_desc for is_active=1 lists

-- pacs_studies: list always filters is_active = 1.
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital;            -- bare hospital_id, left-prefix of all (hospital_id, ...) indexes
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_studies_hospital_date_id_desc; -- superseded by idx_pacs_studies_hospital_study_date_id_desc (WHERE is_active=1 AND study_date IS NOT NULL)

-- pacs_worklists: the worklist list orders by id DESC, so the (hospital_id,
-- status, id DESC) index serves status lookups; the created-ordered duplicate
-- is redundant.
DROP INDEX CONCURRENTLY IF EXISTS idx_pacs_worklists_hospital_status; -- (hospital_id, status, created DESC) covered by idx_pacs_worklists_hospital_status_id_desc
