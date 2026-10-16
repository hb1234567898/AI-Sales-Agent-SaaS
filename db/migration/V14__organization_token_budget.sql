CREATE TABLE organization_token_budget (
    organization_id uuid PRIMARY KEY REFERENCES organization(id) ON DELETE CASCADE,
    total_tokens bigint NOT NULL CHECK (total_tokens >= 0),
    updated_by uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_organization_token_budget_actor
        FOREIGN KEY (organization_id, updated_by)
        REFERENCES organization_member (organization_id, id)
);

-- Preserve already assigned quotas when upgrading an existing installation.
INSERT INTO organization_token_budget (organization_id, total_tokens, updated_by)
SELECT organization_id, SUM(allocated_tokens)::bigint, MIN(updated_by::text)::uuid
FROM member_token_quota
GROUP BY organization_id;

COMMENT ON TABLE organization_token_budget IS '团队内部可分配 Token 总预算，不代表模型供应商账户余额。';
