-- Standardize token lifetimes for frontend and first-party clients.
-- Access token: 1 hour (3600000 ms)
-- Refresh token: 30 days (2592000000 ms)
UPDATE oauth2_clients
SET access_token_lifetime_ms = 3600000,
    refresh_token_lifetime_ms = 2592000000,
    modified = NOW();
