-- V11: 用户上传的客户附件。MVP 阶段直接存入数据库，便于邮件发送前做存在性校验与审计。
CREATE TABLE uploaded_file (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       uuid NOT NULL REFERENCES organization(id),
    uploaded_by_member_id uuid,
    customer_id           uuid REFERENCES customer(id),
    original_filename     varchar(255) NOT NULL,
    content_type          varchar(255) NOT NULL,
    size_bytes            bigint NOT NULL,
    sha256                char(64) NOT NULL,
    content               bytea NOT NULL,
    status                varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at            timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at            timestamptz NOT NULL DEFAULT clock_timestamp(),
    version               bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_uploaded_file_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT ck_uploaded_file_name_not_blank CHECK (btrim(original_filename) <> ''),
    CONSTRAINT ck_uploaded_file_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT ck_uploaded_file_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT fk_uploaded_file_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id)
);

CREATE INDEX ix_uploaded_file_customer_created
    ON uploaded_file (organization_id, customer_id, created_at DESC)
    WHERE status = 'ACTIVE' AND customer_id IS NOT NULL;

COMMENT ON TABLE uploaded_file IS '用户上传的客户附件；邮件发送工具会在执行前按附件 ID 或客户最新附件校验存在性。';
COMMENT ON COLUMN uploaded_file.content IS 'MVP 阶段直接保存文件内容；生产可迁移到对象存储并保留相同附件 ID 契约。';
