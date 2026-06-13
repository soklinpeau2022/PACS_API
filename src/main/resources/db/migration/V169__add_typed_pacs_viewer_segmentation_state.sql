ALTER TABLE pacs_viewer_states
    ADD COLUMN IF NOT EXISTS labelmap_segmentations JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS contour_segmentations JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS surface_segmentations JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS presentation_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS tool_state JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE pacs_viewer_states
SET labelmap_segmentations = COALESCE((
        SELECT jsonb_agg(item)
        FROM jsonb_array_elements(COALESCE(segmentations, '[]'::jsonb)) AS item
        WHERE item ? 'sparseLabelmap'
           OR item #>> '{labelmap,sparseLabelmap}' IS NOT NULL
           OR UPPER(COALESCE(item->>'type', item #>> '{representation,type}', '')) = 'LABELMAP'
           OR COALESCE(item->'representationData', '{}'::jsonb) ? 'Labelmap'
    ), '[]'::jsonb),
    contour_segmentations = COALESCE((
        SELECT jsonb_agg(item)
        FROM jsonb_array_elements(COALESCE(segmentations, '[]'::jsonb)) AS item
        WHERE item #>> '{contour,annotationUIDsBySegment}' IS NOT NULL
           OR UPPER(COALESCE(item->>'type', item #>> '{representation,type}', '')) = 'CONTOUR'
           OR COALESCE(item->'representationData', '{}'::jsonb) ? 'Contour'
    ), '[]'::jsonb),
    surface_segmentations = COALESCE((
        SELECT jsonb_agg(item)
        FROM jsonb_array_elements(COALESCE(segmentations, '[]'::jsonb)) AS item
        WHERE UPPER(COALESCE(item->>'type', item #>> '{representation,type}', '')) = 'SURFACE'
           OR COALESCE(item->'representationData', '{}'::jsonb) ? 'Surface'
    ), '[]'::jsonb)
WHERE jsonb_typeof(COALESCE(segmentations, '[]'::jsonb)) = 'array'
  AND (
      labelmap_segmentations = '[]'::jsonb
      OR contour_segmentations = '[]'::jsonb
      OR surface_segmentations = '[]'::jsonb
  );

CREATE INDEX IF NOT EXISTS idx_pacs_viewer_states_labelmap_gin
    ON pacs_viewer_states USING GIN (labelmap_segmentations)
    WHERE is_active = 1;

CREATE INDEX IF NOT EXISTS idx_pacs_viewer_states_contour_gin
    ON pacs_viewer_states USING GIN (contour_segmentations)
    WHERE is_active = 1;
