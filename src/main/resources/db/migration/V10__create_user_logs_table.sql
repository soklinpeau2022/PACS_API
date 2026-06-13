CREATE TABLE IF NOT EXISTS user_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES users(id),
    type            VARCHAR(120) NOT NULL,
    http_user_agent TEXT,
    remote_addr     VARCHAR(80),
    created         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_logs_user_id ON user_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_user_logs_created ON user_logs(created);
