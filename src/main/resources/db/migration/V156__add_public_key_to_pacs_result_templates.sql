ALTER TABLE pacs_result_templates
    ADD COLUMN IF NOT EXISTS public_id UUID;

UPDATE pacs_result_templates
SET public_id = md5(random()::text || clock_timestamp()::text || id::text)::uuid
WHERE public_id IS NULL;

ALTER TABLE pacs_result_templates
    ALTER COLUMN public_id SET DEFAULT md5(random()::text || clock_timestamp()::text)::uuid;

ALTER TABLE pacs_result_templates
    ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pacs_result_templates_public_id
    ON pacs_result_templates(public_id);
