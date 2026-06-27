INSERT INTO oauth2_clients (
    client_id,
    client_name,
    client_secret_hash,
    client_type,
    allowed_grant_types,
    allowed_scopes,
    access_token_lifetime_ms,
    refresh_token_lifetime_ms,
    is_active,
    created,
    modified
) VALUES
    (
        'pacs-web',
        'PACS Web Client',
        NULL,
        'PUBLIC',
        'password_login,refresh_token',
        'pacs.api pacs.patient.read pacs.patient.write pacs.study.read pacs.viewer.open user.read user.write',
        900000,
        2592000000,
        TRUE,
        NOW(),
        NOW()
    ),
    (
        'pacs-mobile',
        'PACS Mobile Client',
        NULL,
        'PUBLIC',
        'password_login,refresh_token',
        'pacs.api pacs.patient.read pacs.patient.write pacs.study.read pacs.viewer.open user.read',
        900000,
        2592000000,
        TRUE,
        NOW(),
        NOW()
    )
ON CONFLICT (client_id) DO UPDATE
SET
    client_name = EXCLUDED.client_name,
    client_secret_hash = EXCLUDED.client_secret_hash,
    client_type = EXCLUDED.client_type,
    allowed_grant_types = EXCLUDED.allowed_grant_types,
    allowed_scopes = EXCLUDED.allowed_scopes,
    access_token_lifetime_ms = EXCLUDED.access_token_lifetime_ms,
    refresh_token_lifetime_ms = EXCLUDED.refresh_token_lifetime_ms,
    is_active = TRUE,
    modified = NOW();
