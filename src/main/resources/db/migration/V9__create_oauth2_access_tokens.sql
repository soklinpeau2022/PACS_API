CREATE TABLE oauth2_access_tokens (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    username            VARCHAR(100),
    hospital_id         BIGINT,
    hospital_code       VARCHAR(100),
    client_id           VARCHAR(150) NOT NULL DEFAULT 'web',
    permission_version  BIGINT NOT NULL DEFAULT 1,
    roles_csv           TEXT NOT NULL,
    scope               VARCHAR(150) NOT NULL DEFAULT 'pacs.api',
    token_hash          VARCHAR(128) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoked_reason      VARCHAR(255),
    ip_address          VARCHAR(80),
    user_agent          TEXT,
    created             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth2_access_tokens_user_id ON oauth2_access_tokens(user_id);
CREATE INDEX idx_oauth2_access_tokens_expires_at ON oauth2_access_tokens(expires_at);
CREATE INDEX idx_oauth2_access_tokens_active ON oauth2_access_tokens(token_hash, revoked_at, expires_at);
