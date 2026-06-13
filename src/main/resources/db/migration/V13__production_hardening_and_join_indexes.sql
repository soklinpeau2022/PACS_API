-- Production hardening: remove obsolete opaque access-token table and strengthen join-heavy paths.
DROP TABLE IF EXISTS oauth2_access_tokens;

-- Faster RBAC join paths.
CREATE INDEX IF NOT EXISTS idx_role_module_details_role_module ON role_module_details(role_id, module_detail_id);
CREATE INDEX IF NOT EXISTS idx_role_module_details_module_role ON role_module_details(module_detail_id, role_id);
CREATE INDEX IF NOT EXISTS idx_user_groups_user_hospital_role ON user_groups(user_id, hospital_id, role_id);
CREATE INDEX IF NOT EXISTS idx_modules_active_type ON modules(is_active, module_type_id);

-- Refresh-token and revoked-token operational indexes.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_client_active
    ON refresh_tokens(user_id, client_id, revoked_at, expires_at);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_jti_expires
    ON revoked_tokens(jti, expires_at);

-- OAuth2 client hygiene constraints.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_oauth2_clients_type') THEN
        ALTER TABLE oauth2_clients
            ADD CONSTRAINT chk_oauth2_clients_type
            CHECK (client_type IN ('PUBLIC', 'CONFIDENTIAL'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_oauth2_clients_access_lifetime') THEN
        ALTER TABLE oauth2_clients
            ADD CONSTRAINT chk_oauth2_clients_access_lifetime
            CHECK (access_token_lifetime_ms > 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_oauth2_clients_refresh_lifetime') THEN
        ALTER TABLE oauth2_clients
            ADD CONSTRAINT chk_oauth2_clients_refresh_lifetime
            CHECK (refresh_token_lifetime_ms > 0);
    END IF;
END $$;
