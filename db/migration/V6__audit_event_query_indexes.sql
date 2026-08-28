-- 为日志管理页面补充按租户倒序浏览操作日志的索引。
-- V1 已创建 audit_event 表，本迁移只增强查询性能，不改变已有数据。

CREATE INDEX IF NOT EXISTS ix_audit_event_org_occurred
    ON audit_event (organization_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_audit_event_org_result_occurred
    ON audit_event (organization_id, result, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_audit_event_org_action_occurred
    ON audit_event (organization_id, action, occurred_at DESC, id DESC);

COMMENT ON INDEX ix_audit_event_org_occurred IS '支持日志管理页面按组织查看最近操作日志。';
COMMENT ON INDEX ix_audit_event_org_result_occurred IS '支持按执行结果筛选操作日志。';
COMMENT ON INDEX ix_audit_event_org_action_occurred IS '支持按动作类型筛选操作日志。';
