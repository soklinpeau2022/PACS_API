-- Support one-row-per-study projection and indexed linked-worklist filtering.
CREATE INDEX IF NOT EXISTS idx_pacs_worklist_study_links_primary_study_latest
    ON pacs_worklist_study_links (hospital_id, study_id, linked_at DESC, id DESC)
    INCLUDE (worklist_id)
    WHERE is_primary = 1;
