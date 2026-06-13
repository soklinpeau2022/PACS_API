DO $$
DECLARE
    target_table TEXT;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'module_types',
        'modules',
        'module_details',
        'system_activities',
        'user_logs'
    ]
    LOOP
        EXECUTE FORMAT('ALTER TABLE %I ADD COLUMN IF NOT EXISTS public_id UUID', target_table);

        EXECUTE FORMAT(
            'UPDATE %I
             SET public_id = md5(random()::text || clock_timestamp()::text || id::text)::uuid
             WHERE public_id IS NULL',
            target_table
        );

        EXECUTE FORMAT(
            'ALTER TABLE %I ALTER COLUMN public_id SET DEFAULT md5(random()::text || clock_timestamp()::text)::uuid',
            target_table
        );

        EXECUTE FORMAT('ALTER TABLE %I ALTER COLUMN public_id SET NOT NULL', target_table);
        EXECUTE FORMAT('CREATE UNIQUE INDEX IF NOT EXISTS uq_%s_public_id ON %I(public_id)', target_table, target_table);
    END LOOP;
END $$;
