CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(100) NOT NULL UNIQUE,
    email               VARCHAR(255) UNIQUE,
    password            VARCHAR(255) NOT NULL,
    verification_code   VARCHAR(30),
    verification_code_expiry TIMESTAMPTZ,
    first_name          VARCHAR(150),
    last_name           VARCHAR(150),
    telephone           VARCHAR(50),
    signature_photo     TEXT,
    employee_id         BIGINT,
    user_type           SMALLINT NOT NULL DEFAULT 1,
    expire_date         DATE,
    account_locked      BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count  SMALLINT NOT NULL DEFAULT 0,
    password_changed_at TIMESTAMPTZ,
    permission_version  BIGINT NOT NULL DEFAULT 1,
    is_active           SMALLINT NOT NULL DEFAULT 1,
    created_by          BIGINT,
    created             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by         BIGINT,
    modified            TIMESTAMPTZ
);

CREATE TABLE revoked_tokens (
    id          BIGSERIAL PRIMARY KEY,
    jti         VARCHAR(150) NOT NULL UNIQUE,
    user_id     BIGINT,
    hospital_id BIGINT,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason      VARCHAR(255)
);
CREATE INDEX idx_revoked_tokens_jti ON revoked_tokens(jti);
CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens(expires_at);

CREATE TABLE auth_audits (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT,
    hospital_id         BIGINT,
    client_id           VARCHAR(150),
    client_name         VARCHAR(255),
    username_or_email   VARCHAR(255),
    event_type          VARCHAR(80) NOT NULL,
    success             BOOLEAN NOT NULL,
    message             TEXT,
    ip_address          VARCHAR(80),
    user_agent          TEXT,
    created             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_auth_audits_user_id ON auth_audits(user_id);
CREATE INDEX idx_auth_audits_created ON auth_audits(created);
