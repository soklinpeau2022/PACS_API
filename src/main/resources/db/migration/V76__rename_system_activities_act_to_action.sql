DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'system_activities'
          AND column_name = 'act'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'system_activities'
          AND column_name = 'action'
    ) THEN
        ALTER TABLE system_activities RENAME COLUMN act TO action;
    END IF;
END $$;

