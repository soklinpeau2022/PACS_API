CREATE TABLE module_types (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(100) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    name_other    VARCHAR(255),
    display_order INTEGER DEFAULT 0,
    is_active     SMALLINT NOT NULL DEFAULT 1,
    created_by    BIGINT,
    created       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   BIGINT,
    modified      TIMESTAMPTZ
);

CREATE TABLE modules (
    id             BIGSERIAL PRIMARY KEY,
    module_type_id BIGINT NOT NULL REFERENCES module_types(id),
    code           VARCHAR(150) NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    name_other     VARCHAR(255),
    route_path     VARCHAR(255),
    icon           VARCHAR(100),
    display_order  INTEGER DEFAULT 0,
    is_active      SMALLINT NOT NULL DEFAULT 1,
    created_by     BIGINT,
    created        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by    BIGINT,
    modified       TIMESTAMPTZ
);

CREATE TABLE module_details (
    id            BIGSERIAL PRIMARY KEY,
    module_id     BIGINT NOT NULL REFERENCES modules(id),
    code          VARCHAR(200) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    name_other    VARCHAR(255),
    type          VARCHAR(80) NOT NULL,
    action_key    VARCHAR(100),
    display_order INTEGER DEFAULT 0,
    is_active     SMALLINT NOT NULL DEFAULT 1,
    created_by    BIGINT,
    created       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   BIGINT,
    modified      TIMESTAMPTZ
);

CREATE TABLE roles (
    id             BIGSERIAL PRIMARY KEY,
    hospital_id    BIGINT REFERENCES hospitals(id),
    code           VARCHAR(100),
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    is_active      SMALLINT NOT NULL DEFAULT 1,
    created_by     BIGINT,
    created        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by    BIGINT,
    modified       TIMESTAMPTZ,
    UNIQUE(hospital_id, name)
);

CREATE TABLE role_module_details (
    id               BIGSERIAL PRIMARY KEY,
    role_id          BIGINT NOT NULL REFERENCES roles(id),
    module_detail_id BIGINT NOT NULL REFERENCES module_details(id),
    created_by       BIGINT,
    created          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(role_id, module_detail_id)
);

CREATE TABLE user_groups (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    role_id     BIGINT NOT NULL REFERENCES roles(id),
    is_active   SMALLINT NOT NULL DEFAULT 1,
    created_by  BIGINT,
    created     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by BIGINT,
    modified    TIMESTAMPTZ,
    UNIQUE(user_id, hospital_id, role_id)
);
CREATE INDEX idx_user_groups_user_hospital ON user_groups(user_id, hospital_id);

CREATE TABLE endpoint_permissions (
    id               BIGSERIAL PRIMARY KEY,
    http_method      VARCHAR(20) NOT NULL,
    endpoint_pattern VARCHAR(255) NOT NULL,
    permission_code  VARCHAR(200) NOT NULL,
    is_active        SMALLINT NOT NULL DEFAULT 1,
    created          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(http_method, endpoint_pattern, permission_code)
);

CREATE TABLE activity_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    hospital_id BIGINT,
    endpoint    VARCHAR(255),
    module      VARCHAR(100),
    action      VARCHAR(100),
    status      VARCHAR(50),
    ip_address  VARCHAR(80),
    user_agent  TEXT,
    error_msg   TEXT,
    created     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_activity_logs_user_id ON activity_logs(user_id, created DESC);
