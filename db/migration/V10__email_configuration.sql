-- V10: 组织级邮件发送配置。SMTP 密码仅保存 AES-GCM 密文，支持页面在线配置。
CREATE TABLE email_configuration (
    organization_id             uuid PRIMARY KEY REFERENCES organization(id),
    host                        varchar(255) NOT NULL,
    port                        integer NOT NULL,
    username                    varchar(320),
    encrypted_password          text,
    from_address                varchar(320) NOT NULL,
    smtp_auth                   boolean NOT NULL DEFAULT true,
    starttls_enabled            boolean NOT NULL DEFAULT true,
    starttls_required           boolean NOT NULL DEFAULT false,
    encryption_version          smallint NOT NULL DEFAULT 1,
    created_at                  timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at                  timestamptz NOT NULL DEFAULT clock_timestamp(),
    version                     bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_email_configuration_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_email_configuration_host_not_blank CHECK (btrim(host) <> ''),
    CONSTRAINT ck_email_configuration_from_not_blank CHECK (btrim(from_address) <> ''),
    CONSTRAINT ck_email_configuration_encryption_version CHECK (encryption_version > 0),
    CONSTRAINT ck_email_configuration_auth_password CHECK (
        smtp_auth = false OR encrypted_password IS NOT NULL
    )
);

COMMENT ON TABLE email_configuration IS '组织级 SMTP 邮件发送配置；优先于服务器环境变量。';
COMMENT ON COLUMN email_configuration.encrypted_password IS '使用服务器 APP_ENCRYPTION_KEY 进行 AES-256-GCM 加密后的 SMTP 密码或授权码，禁止保存明文。';
