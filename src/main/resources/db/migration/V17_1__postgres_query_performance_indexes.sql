-- PostgreSQL query performance tuning for common API list/search paths.

-- Optional trigram extension for fast ILIKE lookups.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Pagination/list scans.
CREATE INDEX IF NOT EXISTS idx_users_active_id_desc
    ON users (is_active, id DESC);
CREATE INDEX IF NOT EXISTS idx_patients_hospital_active_id_desc
    ON patients (hospital_id, is_active, id DESC);
CREATE INDEX IF NOT EXISTS idx_studies_hospital_active_id_desc
    ON pacs_studies (hospital_id, is_active, id DESC);
CREATE INDEX IF NOT EXISTS idx_queue_hospital_id_desc
    ON pacs_patient_queue (hospital_id, id DESC);

-- Join filters used by auth/user context resolution.
CREATE INDEX IF NOT EXISTS idx_user_hospitals_user_active_default
    ON user_hospitals (user_id, is_active, is_default, hospital_id);
CREATE INDEX IF NOT EXISTS idx_user_groups_user_hospital_active
    ON user_groups (user_id, hospital_id, is_active);

-- Trigram indexes for flexible ILIKE search.
CREATE INDEX IF NOT EXISTS idx_users_username_trgm
    ON users USING gin (username gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_email_trgm
    ON users USING gin (email gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_first_name_trgm
    ON users USING gin (first_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_last_name_trgm
    ON users USING gin (last_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_telephone_trgm
    ON users USING gin (telephone gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_patients_uid_trgm
    ON patients USING gin (patient_uid gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_patients_name_trgm
    ON patients USING gin (name gin_trgm_ops);
