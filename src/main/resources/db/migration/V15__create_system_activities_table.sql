CREATE TABLE IF NOT EXISTS system_activities (
    id               BIGSERIAL PRIMARY KEY,
    endpoint         VARCHAR(255) NOT NULL,
    module           VARCHAR(120),
    module_id        BIGINT REFERENCES modules(id),
    act              VARCHAR(120),
    description      TEXT,
    bug              TEXT,
    line_code        BIGINT,
    browser          VARCHAR(120),
    operating_system VARCHAR(120),
    ip               VARCHAR(80),
    host_name        VARCHAR(255),
    duration         BIGINT,
    created_by       BIGINT REFERENCES users(id),
    created          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status           INT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_system_activities_created
    ON system_activities(created);

CREATE INDEX IF NOT EXISTS idx_system_activities_module_id
    ON system_activities(module_id);

CREATE INDEX IF NOT EXISTS idx_system_activities_created_by
    ON system_activities(created_by);

CREATE INDEX IF NOT EXISTS idx_system_activities_status
    ON system_activities(status);

CREATE INDEX IF NOT EXISTS idx_system_activities_endpoint
    ON system_activities(endpoint);
