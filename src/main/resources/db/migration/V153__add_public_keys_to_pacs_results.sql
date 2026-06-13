ALTER TABLE pacs_results
    ADD COLUMN IF NOT EXISTS result_public_id UUID;

UPDATE pacs_results
SET result_public_id = md5(random()::text || clock_timestamp()::text || id::text)::uuid
WHERE result_public_id IS NULL;

ALTER TABLE pacs_results
    ALTER COLUMN result_public_id SET DEFAULT (md5(random()::text || clock_timestamp()::text)::uuid);

ALTER TABLE pacs_results
    ALTER COLUMN result_public_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_results_public_id
    ON pacs_results (result_public_id);

ALTER TABLE pacs_result_images
    ADD COLUMN IF NOT EXISTS image_public_id UUID;

UPDATE pacs_result_images
SET image_public_id = md5(random()::text || clock_timestamp()::text || id::text)::uuid
WHERE image_public_id IS NULL;

ALTER TABLE pacs_result_images
    ALTER COLUMN image_public_id SET DEFAULT (md5(random()::text || clock_timestamp()::text)::uuid);

ALTER TABLE pacs_result_images
    ALTER COLUMN image_public_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_pacs_result_images_public_id
    ON pacs_result_images (image_public_id);
