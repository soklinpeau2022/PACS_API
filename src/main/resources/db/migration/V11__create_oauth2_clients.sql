CREATE TABLE oauth2_clients (
    id                          BIGSERIAL PRIMARY KEY,
    client_id                   VARCHAR(150) NOT NULL UNIQUE,
    client_name                 VARCHAR(255) NOT NULL,
    client_secret_hash          VARCHAR(128),
    client_type                 VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    allowed_grant_types         TEXT         NOT NULL,
    allowed_scopes              TEXT         NOT NULL,
    access_token_lifetime_ms    BIGINT       NOT NULL DEFAULT 900000,
    refresh_token_lifetime_ms   BIGINT       NOT NULL DEFAULT 2592000000,
    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified                    TIMESTAMPTZ
);

CREATE INDEX idx_oauth2_clients_client_id ON oauth2_clients(client_id);

-- Default first-party public clients (no secret required)
INSERT INTO oauth2_clients (client_id, client_name, client_type, allowed_grant_types, allowed_scopes)
VALUES
    ('pacs-web',    'PACS Web Client',    'PUBLIC', 'password_login,refresh_token',
     'pacs.api pacs.patient.read pacs.patient.write pacs.study.read pacs.viewer.open user.read user.write'),
    ('pacs-mobile', 'PACS Mobile Client', 'PUBLIC', 'password_login,refresh_token',
     'pacs.api pacs.patient.read pacs.patient.write pacs.study.read pacs.viewer.open user.read');

-- Example confidential machine-to-machine client (client_secret must be set via UPDATE with hashed secret)
INSERT INTO oauth2_clients (client_id, client_name, client_type, allowed_grant_types, allowed_scopes)
VALUES
    ('pacs-adapter', 'PACS Integration Adapter', 'CONFIDENTIAL', 'client_credentials',
     'pacs.api pacs.patient.read pacs.study.read');
