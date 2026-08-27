-- 组织级 AI 模型配置。API Key 仅保存 AES-GCM 密文，解密主密钥由服务器环境变量提供。
CREATE TABLE ai_model_configuration (
    organization_id     uuid PRIMARY KEY REFERENCES organization(id),
    provider            varchar(40) NOT NULL DEFAULT 'QWEN',
    model_name          varchar(120) NOT NULL,
    base_url            varchar(500) NOT NULL,
    encrypted_api_key   text NOT NULL,
    encryption_version  smallint NOT NULL DEFAULT 1,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_model_provider CHECK (provider IN ('QWEN')),
    CONSTRAINT ck_ai_model_encryption_version CHECK (encryption_version > 0),
    CONSTRAINT ck_ai_model_name_not_blank CHECK (btrim(model_name) <> ''),
    CONSTRAINT ck_ai_model_base_url_not_blank CHECK (btrim(base_url) <> ''),
    CONSTRAINT ck_ai_model_key_not_blank CHECK (btrim(encrypted_api_key) <> '')
);

COMMENT ON TABLE ai_model_configuration IS '组织级大模型连接配置；每个组织当前只保留一个启用配置。';
COMMENT ON COLUMN ai_model_configuration.encrypted_api_key IS '使用服务器 APP_ENCRYPTION_KEY 进行 AES-256-GCM 加密后的 API Key，禁止保存明文。';
COMMENT ON COLUMN ai_model_configuration.encryption_version IS '密文格式版本，用于后续轮换算法或主密钥。';

