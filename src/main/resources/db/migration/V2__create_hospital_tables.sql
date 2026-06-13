CREATE TABLE hospitals (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    name_other          VARCHAR(255),
    ae_title            VARCHAR(64),
    dicomweb_base_url   TEXT,
    dicom_host          VARCHAR(255),
    dicom_port          INTEGER,
    timezone            VARCHAR(80) DEFAULT 'Asia/Phnom_Penh',
    is_active           SMALLINT NOT NULL DEFAULT 1,
    created_by          BIGINT,
    created             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by         BIGINT,
    modified            TIMESTAMPTZ
);

CREATE TABLE user_hospitals (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    is_active   SMALLINT NOT NULL DEFAULT 1,
    created_by  BIGINT,
    created     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by BIGINT,
    modified    TIMESTAMPTZ,
    UNIQUE(user_id, hospital_id)
);
CREATE INDEX idx_user_hospitals_user_id ON user_hospitals(user_id);
CREATE INDEX idx_user_hospitals_hospital_id ON user_hospitals(hospital_id);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    hospital_id     BIGINT NOT NULL REFERENCES hospitals(id),
    client_id       VARCHAR(150) NOT NULL,
    client_name     VARCHAR(255),
    token_hash      VARCHAR(128) NOT NULL UNIQUE,
    rotated_from_id BIGINT,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  VARCHAR(255),
    ip_address      VARCHAR(80),
    user_agent      TEXT,
    created         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);
