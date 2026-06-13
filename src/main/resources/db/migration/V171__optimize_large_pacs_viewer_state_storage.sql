ALTER TABLE pacs_viewer_states
    ADD COLUMN IF NOT EXISTS payload_size_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payload_sha256 CHAR(64),
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

UPDATE pacs_viewer_states
SET payload_size_bytes =
      OCTET_LENGTH(COALESCE(viewer_state, '{}'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(measurements, '[]'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(annotations, '[]'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(segmentations, '[]'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(labelmap_segmentations, '[]'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(contour_segmentations, '[]'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(surface_segmentations, '[]'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(additional_findings, '[]'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(presentation_state, '{}'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(tool_state, '{}'::jsonb)::text)
    + OCTET_LENGTH(COALESCE(metadata, '{}'::jsonb)::text)
WHERE payload_size_bytes = 0
  AND is_active = 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_pacs_viewer_states_deleted_by'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT fk_pacs_viewer_states_deleted_by
            FOREIGN KEY (deleted_by) REFERENCES users(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_pacs_viewer_states_payload_size'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT ck_pacs_viewer_states_payload_size
            CHECK (payload_size_bytes BETWEEN 0 AND 10485760) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_pacs_viewer_states_payload_sha256'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT ck_pacs_viewer_states_payload_sha256
            CHECK (payload_sha256 IS NULL OR payload_sha256 ~ '^[0-9a-f]{64}$') NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_pacs_viewer_states_active_status'
    ) THEN
        ALTER TABLE pacs_viewer_states
            ADD CONSTRAINT ck_pacs_viewer_states_active_status
            CHECK (is_active IN (1, 2)) NOT VALID;
    END IF;
END $$;

-- Viewer-state lookups are scope based; no production query searches inside
-- segmentation JSON. These GIN indexes multiplied write cost for large payloads.
DROP INDEX IF EXISTS idx_pacs_viewer_states_labelmap_gin;
DROP INDEX IF EXISTS idx_pacs_viewer_states_contour_gin;
