-- Big-data hardening for queue-result workloads (10M+ rows).
-- Focus:
-- 1) Cursor pagination path (hospital_id + is_active + id DESC)
-- 2) Queue scoped lookups (hospital_id + queue_id + is_active)
-- 3) ILIKE search on result description

CREATE INDEX IF NOT EXISTS idx_queue_results_hospital_active_id_desc
    ON pacs_queue_results (hospital_id, is_active, id DESC);

CREATE INDEX IF NOT EXISTS idx_queue_results_hospital_queue_active
    ON pacs_queue_results (hospital_id, queue_id, is_active);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_queue_results_description_trgm
    ON pacs_queue_results USING gin (description gin_trgm_ops);
