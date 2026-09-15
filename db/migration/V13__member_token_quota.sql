-- V13: attribute model usage to members and allow administrators to assign token budgets.
ALTER TABLE model_call
    ADD COLUMN member_id uuid;

ALTER TABLE model_call
    ADD CONSTRAINT fk_model_call_member
        FOREIGN KEY (organization_id, member_id)
        REFERENCES organization_member (organization_id, id);

CREATE INDEX ix_model_call_member_usage
    ON model_call (organization_id, member_id, completed_at)
    WHERE status = 'SUCCEEDED';

CREATE TABLE member_token_quota (
    organization_id  uuid NOT NULL,
    member_id        uuid NOT NULL,
    allocated_tokens bigint NOT NULL,
    updated_by       uuid NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (organization_id, member_id),
    CONSTRAINT fk_member_token_quota_member
        FOREIGN KEY (organization_id, member_id)
        REFERENCES organization_member (organization_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_member_token_quota_actor
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_member_token_quota_non_negative CHECK (allocated_tokens >= 0)
);

COMMENT ON TABLE member_token_quota IS '组织管理员为成员分配的模型 Token 总预算；无记录或额度为 0 时禁止使用 MCP 助手。';
