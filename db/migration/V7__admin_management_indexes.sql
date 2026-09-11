-- 管理员成员列表通常按组织分页，并按角色、状态筛选。
-- 该索引先定位组织，再缩小角色和状态范围，减少成员较多时的全表扫描。
CREATE INDEX ix_organization_member_admin_query
    ON organization_member (organization_id, role, status, created_at);

COMMENT ON INDEX ix_organization_member_admin_query IS
    '加速管理员按组织、角色、状态筛选和分页查询成员；成员量较小时收益不明显，规模增长后可降低扫描成本。';
