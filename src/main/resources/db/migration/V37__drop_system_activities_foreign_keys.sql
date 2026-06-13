ALTER TABLE IF EXISTS system_activities
    DROP CONSTRAINT IF EXISTS system_activities_created_by_fkey;

ALTER TABLE IF EXISTS system_activities
    DROP CONSTRAINT IF EXISTS system_activities_module_id_fkey;
