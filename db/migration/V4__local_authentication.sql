-- 本地账号密码凭据与服务端会话。
-- 数据库只保存 BCrypt 密码摘要和会话令牌的 SHA-256 摘要，不保存明文密码或原始令牌。
CREATE TABLE local_user_credential (
    user_id                 uuid PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    password_hash           varchar(100) NOT NULL,
    failed_attempts         integer NOT NULL DEFAULT 0,
    locked_until            timestamptz,
    password_changed_at     timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_at              timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at              timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_local_user_credential_failed_attempts CHECK (failed_attempts BETWEEN 0 AND 100)
);

CREATE TABLE auth_session (
    id                      uuid PRIMARY KEY,
    user_id                 uuid NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    organization_id         uuid NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    member_id               uuid NOT NULL,
    token_hash              char(64) NOT NULL,
    expires_at              timestamptz NOT NULL,
    last_seen_at            timestamptz NOT NULL,
    user_agent              varchar(500),
    ip_address              varchar(64),
    created_at              timestamptz NOT NULL,
    revoked_at              timestamptz,
    CONSTRAINT uq_auth_session_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_session_member
        FOREIGN KEY (organization_id, member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_auth_session_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_auth_session_user_active
    ON auth_session (user_id, expires_at DESC)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_auth_session_expiry
    ON auth_session (expires_at)
    WHERE revoked_at IS NULL;

CREATE TRIGGER trg_local_user_credential_updated_at
BEFORE UPDATE ON local_user_credential FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE local_user_credential IS 'LOCAL 身份提供商使用的密码凭据、失败次数和临时锁定状态。';
COMMENT ON COLUMN local_user_credential.password_hash IS '使用 BCrypt 保存的单向密码摘要，禁止保存或记录明文密码。';
COMMENT ON TABLE auth_session IS '浏览器登录后的服务端会话，Cookie 中的原始令牌只在客户端保存。';
COMMENT ON COLUMN auth_session.token_hash IS '随机会话令牌的 SHA-256 十六进制摘要，用于数据库泄露时降低令牌复用风险。';
COMMENT ON COLUMN auth_session.revoked_at IS '退出登录或管理员撤销会话的时间；非空会话不能继续使用。';
