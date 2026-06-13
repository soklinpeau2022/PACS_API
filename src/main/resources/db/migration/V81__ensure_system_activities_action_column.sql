DO $$
BEGIN
    -- If legacy "act" exists but "action" is missing, create "action" and copy values.
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'system_activities'
          AND column_name = 'act'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'system_activities'
          AND column_name = 'action'
    ) THEN
        ALTER TABLE system_activities ADD COLUMN action VARCHAR(120);
        UPDATE system_activities
        SET action = act
        WHERE action IS NULL;
    END IF;

    -- Safety net: if both are missing for any reason, create "action".
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'system_activities'
          AND column_name = 'action'
    ) THEN
        ALTER TABLE system_activities ADD COLUMN action VARCHAR(120);
    END IF;
END $$;

