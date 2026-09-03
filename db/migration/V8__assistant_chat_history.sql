-- -----------------------------------------------------------------------------
-- MCP 自动化助手会话历史
-- -----------------------------------------------------------------------------
-- 设计目标：
-- 1. 让浏览器刷新、换设备后仍能查看 MCP 助手聊天记录。
-- 2. 为后续桌面端复用同一套会话历史和工具执行轨迹做准备。
-- 3. 保存的是可审计的执行摘要、工具调用轨迹和业务结果，不保存模型不可见的原始推理链。

CREATE TABLE assistant_conversation (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    user_id             uuid REFERENCES app_user(id),
    member_id           uuid REFERENCES organization_member(id),
    title               varchar(200) NOT NULL DEFAULT '新的自动化会话',
    channel             varchar(40) NOT NULL DEFAULT 'WEB',
    status              varchar(30) NOT NULL DEFAULT 'OPEN',
    last_message_at     timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             integer NOT NULL DEFAULT 0
);

COMMENT ON TABLE assistant_conversation IS 'MCP 自动化助手会话，一个会话包含多轮用户指令和助手回复';
COMMENT ON COLUMN assistant_conversation.organization_id IS '所属组织，用于多租户隔离';
COMMENT ON COLUMN assistant_conversation.user_id IS '发起会话的登录用户';
COMMENT ON COLUMN assistant_conversation.member_id IS '发起会话的组织成员身份';
COMMENT ON COLUMN assistant_conversation.title IS '会话标题，默认取用户第一条指令的前若干字';
COMMENT ON COLUMN assistant_conversation.channel IS '会话来源，例如 WEB、DESKTOP，方便后续桌面端接入';
COMMENT ON COLUMN assistant_conversation.status IS '会话状态，OPEN 表示可继续对话';
COMMENT ON COLUMN assistant_conversation.last_message_at IS '最后一条消息时间，用于会话列表排序';

CREATE INDEX ix_assistant_conversation_org_member_recent
    ON assistant_conversation (organization_id, member_id, last_message_at DESC, created_at DESC);

CREATE INDEX ix_assistant_conversation_org_recent
    ON assistant_conversation (organization_id, last_message_at DESC, created_at DESC);

CREATE TABLE assistant_message (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    conversation_id     uuid NOT NULL REFERENCES assistant_conversation(id) ON DELETE CASCADE,
    role                varchar(30) NOT NULL,
    content             text NOT NULL,
    reasoning_summary   text,
    tool_traces         jsonb NOT NULL DEFAULT '[]'::jsonb,
    data                jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_assistant_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

COMMENT ON TABLE assistant_message IS 'MCP 自动化助手消息历史，保存用户指令、助手回复和工具调用结果';
COMMENT ON COLUMN assistant_message.organization_id IS '所属组织，用于多租户隔离';
COMMENT ON COLUMN assistant_message.conversation_id IS '所属会话';
COMMENT ON COLUMN assistant_message.role IS '消息角色：USER 用户、ASSISTANT 助手、SYSTEM 系统、TOOL 工具';
COMMENT ON COLUMN assistant_message.content IS '消息正文';
COMMENT ON COLUMN assistant_message.reasoning_summary IS '可展示的执行摘要，不等同于模型原始推理链';
COMMENT ON COLUMN assistant_message.tool_traces IS '工具调用轨迹，记录调用了哪些业务工具以及结果';
COMMENT ON COLUMN assistant_message.data IS '结构化业务结果，例如客户 ID、审批 ID、Agent 运行 ID';

CREATE INDEX ix_assistant_message_conversation_time
    ON assistant_message (conversation_id, created_at, id);

CREATE INDEX ix_assistant_message_org_time
    ON assistant_message (organization_id, created_at DESC);
