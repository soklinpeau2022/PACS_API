DROP INDEX IF EXISTS idx_system_activities_lower_action_module_created_id;

CREATE INDEX idx_system_activities_lower_action_module_created_id
    ON system_activities (
        LOWER(COALESCE(action, '')),
        LOWER(COALESCE(module, '')),
        created DESC,
        id DESC
    );
