-- AI 销售跟进 Agent - PostgreSQL 基线数据库结构
-- 目标数据库：PostgreSQL 15 及以上版本
-- 迁移方式：Flyway 单向迁移，不在生产环境回滚修改过的历史迁移

SET TIME ZONE 'UTC';

-- PostgreSQL 15+ 已原生提供 gen_random_uuid()，无需依赖 pgcrypto。
-- 为兼容宝塔等精简版 PostgreSQL，大小写不敏感唯一性使用 lower(...) 函数索引实现，
-- 避免依赖可能未随服务端安装的 citext 扩展。

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

-- -----------------------------------------------------------------------------
-- 身份与多租户
-- -----------------------------------------------------------------------------

CREATE TABLE organization (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                varchar(64) NOT NULL,
    name                varchar(200) NOT NULL,
    timezone            varchar(64) NOT NULL DEFAULT 'UTC',
    locale              varchar(16) NOT NULL DEFAULT 'zh-CN',
    plan_code           varchar(50) NOT NULL DEFAULT 'STARTER',
    status              varchar(24) NOT NULL DEFAULT 'ACTIVE',
    settings            jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_organization_slug CHECK (slug::text ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_organization_settings_object CHECK (jsonb_typeof(settings) = 'object')
);

CREATE UNIQUE INDEX uq_organization_slug_ci
    ON organization (lower(slug));

CREATE TABLE app_user (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email               varchar(320) NOT NULL,
    display_name        varchar(120) NOT NULL,
    auth_provider       varchar(50) NOT NULL,
    auth_subject        varchar(255) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'ACTIVE',
    locale              varchar(16) NOT NULL DEFAULT 'zh-CN',
    last_login_at       timestamptz,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_user_auth_subject UNIQUE (auth_provider, auth_subject),
    CONSTRAINT ck_app_user_status CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_app_user_email_ci
    ON app_user (lower(email));

CREATE TABLE organization_member (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    user_id             uuid NOT NULL REFERENCES app_user(id),
    role                varchar(24) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'ACTIVE',
    permissions         jsonb NOT NULL DEFAULT '[]'::jsonb,
    joined_at           timestamptz,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_organization_member_user UNIQUE (organization_id, user_id),
    CONSTRAINT uq_organization_member_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT ck_organization_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'SALES', 'VIEWER')),
    CONSTRAINT ck_organization_member_status CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'LEFT')),
    CONSTRAINT ck_organization_member_permissions_array CHECK (jsonb_typeof(permissions) = 'array')
);

CREATE INDEX ix_organization_member_user_status
    ON organization_member (user_id, status);

-- -----------------------------------------------------------------------------
-- 外部系统集成
-- -----------------------------------------------------------------------------

CREATE TABLE integration_connection (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    kind                varchar(24) NOT NULL,
    provider            varchar(40) NOT NULL,
    display_name        varchar(120) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'PENDING',
    external_account_id varchar(255),
    external_account_name varchar(255),
    credential_ref      varchar(500),
    scopes              text[] NOT NULL DEFAULT ARRAY[]::text[],
    settings            jsonb NOT NULL DEFAULT '{}'::jsonb,
    connected_by_member_id uuid,
    connected_at        timestamptz,
    expires_at          timestamptz,
    last_sync_at        timestamptz,
    last_error_code     varchar(80),
    last_error_message  text,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_integration_connection_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT fk_integration_connected_member
        FOREIGN KEY (organization_id, connected_by_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_integration_kind CHECK (kind IN ('EMAIL', 'CRM', 'CALENDAR')),
    CONSTRAINT ck_integration_status CHECK (status IN ('PENDING', 'ACTIVE', 'REAUTH_REQUIRED', 'ERROR', 'DISABLED')),
    CONSTRAINT ck_integration_settings_object CHECK (jsonb_typeof(settings) = 'object')
);

CREATE UNIQUE INDEX uq_integration_external_account
    ON integration_connection (organization_id, provider, external_account_id)
    WHERE external_account_id IS NOT NULL AND status <> 'DISABLED';

CREATE INDEX ix_integration_org_kind_status
    ON integration_connection (organization_id, kind, status);

CREATE TABLE integration_sync_cursor (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    connection_id       uuid NOT NULL,
    resource_type       varchar(60) NOT NULL,
    cursor_value        text,
    watermark_at        timestamptz,
    status              varchar(24) NOT NULL DEFAULT 'IDLE',
    lease_owner         varchar(160),
    lease_expires_at    timestamptz,
    last_started_at     timestamptz,
    last_completed_at   timestamptz,
    last_error          text,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_integration_sync_resource UNIQUE (organization_id, connection_id, resource_type),
    CONSTRAINT uq_integration_sync_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT fk_integration_sync_connection
        FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, id),
    CONSTRAINT ck_integration_sync_status CHECK (status IN ('IDLE', 'RUNNING', 'FAILED', 'DISABLED'))
);

-- -----------------------------------------------------------------------------
-- 销售业务领域
-- -----------------------------------------------------------------------------

CREATE TABLE customer (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    owner_member_id     uuid,
    name                varchar(255) NOT NULL,
    website             varchar(500),
    industry            varchar(120),
    employee_range      varchar(40),
    stage               varchar(32) NOT NULL DEFAULT 'LEAD',
    status              varchar(24) NOT NULL DEFAULT 'ACTIVE',
    source              varchar(40) NOT NULL DEFAULT 'MANUAL',
    external_id         varchar(255),
    last_interaction_at timestamptz,
    next_follow_up_at   timestamptz,
    attributes          jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    deleted_at          timestamptz,
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT fk_customer_owner
        FOREIGN KEY (organization_id, owner_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_customer_stage CHECK (stage IN ('LEAD', 'QUALIFIED', 'DISCOVERY', 'DEMO', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_customer_attributes_object CHECK (jsonb_typeof(attributes) = 'object')
);

CREATE UNIQUE INDEX uq_customer_external_source
    ON customer (organization_id, source, external_id)
    WHERE external_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_customer_org_owner_status
    ON customer (organization_id, owner_member_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_customer_org_next_follow_up
    ON customer (organization_id, next_follow_up_at)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX ix_customer_org_last_interaction
    ON customer (organization_id, last_interaction_at DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE TABLE customer_contact (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    customer_id         uuid NOT NULL,
    first_name          varchar(100),
    last_name           varchar(100),
    full_name           varchar(220) NOT NULL,
    email               varchar(320),
    phone               varchar(50),
    job_title           varchar(120),
    is_primary          boolean NOT NULL DEFAULT false,
    source              varchar(40) NOT NULL DEFAULT 'MANUAL',
    external_id         varchar(255),
    attributes          jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    deleted_at          timestamptz,
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_contact_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uq_customer_contact_customer_id UNIQUE (organization_id, customer_id, id),
    CONSTRAINT fk_customer_contact_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT ck_customer_contact_attributes_object CHECK (jsonb_typeof(attributes) = 'object')
);

CREATE UNIQUE INDEX uq_customer_contact_external_source
    ON customer_contact (organization_id, source, external_id)
    WHERE external_id IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_customer_primary_contact
    ON customer_contact (organization_id, customer_id)
    WHERE is_primary = true AND deleted_at IS NULL;

CREATE INDEX ix_customer_contact_email
    ON customer_contact (organization_id, email)
    WHERE email IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE opportunity (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    customer_id         uuid NOT NULL,
    owner_member_id     uuid,
    name                varchar(255) NOT NULL,
    stage               varchar(32) NOT NULL DEFAULT 'QUALIFIED',
    status              varchar(24) NOT NULL DEFAULT 'OPEN',
    amount              numeric(19,4),
    currency            char(3),
    probability         smallint,
    expected_close_date date,
    source              varchar(40) NOT NULL DEFAULT 'MANUAL',
    external_id         varchar(255),
    attributes          jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    closed_at           timestamptz,
    deleted_at          timestamptz,
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_opportunity_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uq_opportunity_customer_id UNIQUE (organization_id, customer_id, id),
    CONSTRAINT fk_opportunity_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT fk_opportunity_owner
        FOREIGN KEY (organization_id, owner_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_opportunity_stage CHECK (stage IN ('QUALIFIED', 'DISCOVERY', 'DEMO', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    CONSTRAINT ck_opportunity_status CHECK (status IN ('OPEN', 'WON', 'LOST', 'ARCHIVED')),
    CONSTRAINT ck_opportunity_amount CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT ck_opportunity_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_opportunity_probability CHECK (probability IS NULL OR probability BETWEEN 0 AND 100),
    CONSTRAINT ck_opportunity_attributes_object CHECK (jsonb_typeof(attributes) = 'object')
);

CREATE UNIQUE INDEX uq_opportunity_external_source
    ON opportunity (organization_id, source, external_id)
    WHERE external_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_opportunity_customer_status
    ON opportunity (organization_id, customer_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_opportunity_org_stage_close
    ON opportunity (organization_id, stage, expected_close_date)
    WHERE status = 'OPEN' AND deleted_at IS NULL;

CREATE TABLE interaction (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    customer_id         uuid NOT NULL,
    contact_id          uuid,
    opportunity_id      uuid,
    created_by_member_id uuid,
    type                varchar(32) NOT NULL,
    direction           varchar(16) NOT NULL DEFAULT 'NONE',
    occurred_at         timestamptz NOT NULL,
    subject             varchar(500),
    body_text           text,
    body_preview        varchar(1000),
    participants        jsonb NOT NULL DEFAULT '[]'::jsonb,
    source              varchar(40) NOT NULL DEFAULT 'INTERNAL',
    external_id         varchar(500),
    source_payload      jsonb NOT NULL DEFAULT '{}'::jsonb,
    metadata            jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_interaction_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT fk_interaction_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT fk_interaction_contact
        FOREIGN KEY (organization_id, customer_id, contact_id)
        REFERENCES customer_contact (organization_id, customer_id, id),
    CONSTRAINT fk_interaction_opportunity
        FOREIGN KEY (organization_id, customer_id, opportunity_id)
        REFERENCES opportunity (organization_id, customer_id, id),
    CONSTRAINT fk_interaction_created_by
        FOREIGN KEY (organization_id, created_by_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_interaction_type CHECK (type IN (
        'EMAIL_SENT', 'EMAIL_RECEIVED', 'EMAIL_OPENED', 'CALL', 'MEETING',
        'NOTE', 'TASK_CREATED', 'TASK_COMPLETED', 'CRM_UPDATE'
    )),
    CONSTRAINT ck_interaction_direction CHECK (direction IN ('INBOUND', 'OUTBOUND', 'NONE')),
    CONSTRAINT ck_interaction_participants_array CHECK (jsonb_typeof(participants) = 'array'),
    CONSTRAINT ck_interaction_source_payload_object CHECK (jsonb_typeof(source_payload) = 'object'),
    CONSTRAINT ck_interaction_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE UNIQUE INDEX uq_interaction_external_source
    ON interaction (organization_id, source, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX ix_interaction_customer_timeline
    ON interaction (organization_id, customer_id, occurred_at DESC, id DESC);

CREATE INDEX ix_interaction_opportunity_timeline
    ON interaction (organization_id, opportunity_id, occurred_at DESC)
    WHERE opportunity_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Agent 配置与运行时
-- -----------------------------------------------------------------------------

CREATE TABLE agent_config (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    agent_type          varchar(40) NOT NULL DEFAULT 'SALES_FOLLOW_UP',
    name                varchar(120) NOT NULL,
    enabled             boolean NOT NULL DEFAULT true,
    schedule_enabled    boolean NOT NULL DEFAULT false,
    schedule_cron       varchar(120),
    schedule_timezone   varchar(64) NOT NULL DEFAULT 'UTC',
    model_profile       varchar(80) NOT NULL DEFAULT 'default',
    prompt_version      varchar(80) NOT NULL,
    schema_version      varchar(80) NOT NULL,
    config              jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_agent_config_type_name UNIQUE (organization_id, agent_type, name),
    CONSTRAINT uq_agent_config_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT ck_agent_config_type CHECK (agent_type IN ('SALES_FOLLOW_UP')),
    CONSTRAINT ck_agent_schedule CHECK (
        (schedule_enabled = false) OR
        (schedule_enabled = true AND schedule_cron IS NOT NULL)
    ),
    CONSTRAINT ck_agent_config_object CHECK (jsonb_typeof(config) = 'object')
);

CREATE TABLE agent_run (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    agent_config_id     uuid NOT NULL,
    initiated_by_member_id uuid,
    parent_run_id       uuid,
    trigger_type        varchar(24) NOT NULL,
    status              varchar(32) NOT NULL DEFAULT 'CREATED',
    business_date       date NOT NULL,
    deduplication_key   varchar(255),
    scope               jsonb NOT NULL DEFAULT '{}'::jsonb,
    input_snapshot      jsonb NOT NULL DEFAULT '{}'::jsonb,
    output_summary      jsonb NOT NULL DEFAULT '{}'::jsonb,
    total_candidates    integer NOT NULL DEFAULT 0,
    processed_count     integer NOT NULL DEFAULT 0,
    succeeded_count     integer NOT NULL DEFAULT 0,
    skipped_count       integer NOT NULL DEFAULT 0,
    failed_count        integer NOT NULL DEFAULT 0,
    pending_approval_count integer NOT NULL DEFAULT 0,
    last_event_sequence bigint NOT NULL DEFAULT 0,
    error_code          varchar(80),
    error_message       text,
    lease_owner         varchar(160),
    lease_expires_at    timestamptz,
    heartbeat_at        timestamptz,
    queued_at           timestamptz,
    started_at          timestamptz,
    completed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_agent_run_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT fk_agent_run_config
        FOREIGN KEY (organization_id, agent_config_id)
        REFERENCES agent_config (organization_id, id),
    CONSTRAINT fk_agent_run_initiator
        FOREIGN KEY (organization_id, initiated_by_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT fk_agent_run_parent
        FOREIGN KEY (organization_id, parent_run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT ck_agent_run_trigger CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'WEBHOOK', 'RETRY')),
    CONSTRAINT ck_agent_run_status CHECK (status IN (
        'CREATED', 'QUEUED', 'RUNNING', 'WAITING_APPROVAL',
        'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT ck_agent_run_scope_object CHECK (jsonb_typeof(scope) = 'object'),
    CONSTRAINT ck_agent_run_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT ck_agent_run_output_object CHECK (jsonb_typeof(output_summary) = 'object'),
    CONSTRAINT ck_agent_run_counts CHECK (
        total_candidates >= 0 AND processed_count >= 0 AND succeeded_count >= 0 AND
        skipped_count >= 0 AND failed_count >= 0 AND pending_approval_count >= 0
    ),
    CONSTRAINT ck_agent_run_completed_at CHECK (
        completed_at IS NULL OR status IN ('COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE UNIQUE INDEX uq_agent_run_deduplication
    ON agent_run (organization_id, deduplication_key)
    WHERE deduplication_key IS NOT NULL;

CREATE INDEX ix_agent_run_queue
    ON agent_run (status, lease_expires_at, created_at)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX ix_agent_run_org_created
    ON agent_run (organization_id, created_at DESC, id DESC);

CREATE TABLE agent_step (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    run_id              uuid NOT NULL,
    customer_id         uuid,
    parent_step_id      uuid,
    sequence_no         bigint NOT NULL,
    step_type           varchar(32) NOT NULL,
    name                varchar(160) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'STARTED',
    attempt_no          integer NOT NULL DEFAULT 1,
    input_snapshot      jsonb NOT NULL DEFAULT '{}'::jsonb,
    output_snapshot     jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_code          varchar(80),
    error_message       text,
    started_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at        timestamptz,
    duration_ms         bigint,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_agent_step_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uq_agent_step_run_id UNIQUE (organization_id, run_id, id),
    CONSTRAINT uq_agent_step_sequence UNIQUE (organization_id, run_id, sequence_no),
    CONSTRAINT fk_agent_step_run
        FOREIGN KEY (organization_id, run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT fk_agent_step_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT fk_agent_step_parent
        FOREIGN KEY (organization_id, run_id, parent_step_id)
        REFERENCES agent_step (organization_id, run_id, id),
    CONSTRAINT ck_agent_step_type CHECK (step_type IN (
        'SYSTEM', 'LOAD_DATA', 'MODEL_CALL', 'DECISION', 'ACTION_PROPOSED',
        'APPROVAL_WAIT', 'TOOL_CALL', 'TOOL_RESULT', 'ERROR'
    )),
    CONSTRAINT ck_agent_step_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'SKIPPED', 'CANCELLED')),
    CONSTRAINT ck_agent_step_attempt CHECK (attempt_no > 0),
    CONSTRAINT ck_agent_step_duration CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_agent_step_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT ck_agent_step_output_object CHECK (jsonb_typeof(output_snapshot) = 'object')
);

CREATE INDEX ix_agent_step_run_sequence
    ON agent_step (organization_id, run_id, sequence_no);

CREATE INDEX ix_agent_step_customer_created
    ON agent_step (organization_id, customer_id, created_at DESC)
    WHERE customer_id IS NOT NULL;

CREATE TABLE agent_run_event (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id     uuid NOT NULL REFERENCES organization(id),
    run_id              uuid NOT NULL,
    step_id             uuid,
    sequence_no         bigint NOT NULL,
    event_type          varchar(100) NOT NULL,
    payload             jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_agent_run_event_sequence UNIQUE (organization_id, run_id, sequence_no),
    CONSTRAINT fk_agent_run_event_run
        FOREIGN KEY (organization_id, run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT fk_agent_run_event_step
        FOREIGN KEY (organization_id, run_id, step_id)
        REFERENCES agent_step (organization_id, run_id, id),
    CONSTRAINT ck_agent_run_event_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX ix_agent_run_event_replay
    ON agent_run_event (organization_id, run_id, sequence_no);

CREATE INDEX ix_agent_run_event_retention
    ON agent_run_event (occurred_at);

CREATE TABLE model_call (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    run_id              uuid NOT NULL,
    step_id             uuid,
    customer_id         uuid,
    purpose             varchar(40) NOT NULL,
    provider            varchar(80) NOT NULL,
    model               varchar(120) NOT NULL,
    provider_request_id varchar(255),
    prompt_version      varchar(80) NOT NULL,
    schema_version      varchar(80),
    status              varchar(24) NOT NULL DEFAULT 'STARTED',
    attempt_no          integer NOT NULL DEFAULT 1,
    input_tokens        bigint,
    output_tokens       bigint,
    cached_input_tokens bigint,
    estimated_cost      numeric(19,8),
    cost_currency       char(3),
    pricing_version     varchar(80),
    latency_ms          bigint,
    input_hash          char(64),
    output_hash         char(64),
    input_snapshot      jsonb NOT NULL DEFAULT '{}'::jsonb,
    output_snapshot     jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_code          varchar(80),
    error_message       text,
    started_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_model_call_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT fk_model_call_run
        FOREIGN KEY (organization_id, run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT fk_model_call_step
        FOREIGN KEY (organization_id, run_id, step_id)
        REFERENCES agent_step (organization_id, run_id, id),
    CONSTRAINT fk_model_call_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT ck_model_call_purpose CHECK (purpose IN ('SALES_ANALYSIS', 'EMAIL_DRAFT', 'SUMMARY')),
    CONSTRAINT ck_model_call_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_model_call_attempt CHECK (attempt_no > 0),
    CONSTRAINT ck_model_call_tokens CHECK (
        (input_tokens IS NULL OR input_tokens >= 0) AND
        (output_tokens IS NULL OR output_tokens >= 0) AND
        (cached_input_tokens IS NULL OR cached_input_tokens >= 0)
    ),
    CONSTRAINT ck_model_call_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0),
    CONSTRAINT ck_model_call_currency CHECK (cost_currency IS NULL OR cost_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_model_call_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT ck_model_call_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT ck_model_call_output_object CHECK (jsonb_typeof(output_snapshot) = 'object')
);

CREATE INDEX ix_model_call_run_created
    ON model_call (organization_id, run_id, created_at);

CREATE INDEX ix_model_call_usage_period
    ON model_call (organization_id, created_at, provider, model)
    WHERE status = 'SUCCEEDED';

-- -----------------------------------------------------------------------------
-- 客户跟进队列
-- -----------------------------------------------------------------------------

CREATE TABLE follow_up (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    customer_id         uuid NOT NULL,
    opportunity_id      uuid,
    owner_member_id     uuid,
    agent_config_id     uuid,
    source_run_id       uuid,
    origin              varchar(16) NOT NULL DEFAULT 'AI',
    status              varchar(24) NOT NULL DEFAULT 'OPEN',
    due_at              timestamptz NOT NULL,
    priority            smallint NOT NULL,
    ai_score            smallint,
    score_version       varchar(40) NOT NULL,
    score_breakdown     jsonb NOT NULL DEFAULT '{}'::jsonb,
    intent_level        varchar(16),
    risk_level          varchar(16),
    reason              text NOT NULL,
    recommended_action_type varchar(40) NOT NULL,
    recommended_action  jsonb NOT NULL DEFAULT '{}'::jsonb,
    evidence            jsonb NOT NULL DEFAULT '[]'::jsonb,
    snoozed_until       timestamptz,
    completed_at        timestamptz,
    dismissed_at        timestamptz,
    dismissal_reason    text,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_follow_up_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uq_follow_up_customer_id UNIQUE (organization_id, customer_id, id),
    CONSTRAINT fk_follow_up_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT fk_follow_up_opportunity
        FOREIGN KEY (organization_id, customer_id, opportunity_id)
        REFERENCES opportunity (organization_id, customer_id, id),
    CONSTRAINT fk_follow_up_owner
        FOREIGN KEY (organization_id, owner_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT fk_follow_up_agent_config
        FOREIGN KEY (organization_id, agent_config_id)
        REFERENCES agent_config (organization_id, id),
    CONSTRAINT fk_follow_up_source_run
        FOREIGN KEY (organization_id, source_run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT ck_follow_up_origin CHECK (origin IN ('AI', 'MANUAL', 'IMPORT')),
    CONSTRAINT ck_follow_up_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'SNOOZED', 'COMPLETED', 'DISMISSED', 'CANCELLED')),
    CONSTRAINT ck_follow_up_priority CHECK (priority BETWEEN 0 AND 100),
    CONSTRAINT ck_follow_up_ai_score CHECK (ai_score IS NULL OR ai_score BETWEEN 0 AND 100),
    CONSTRAINT ck_follow_up_intent CHECK (intent_level IS NULL OR intent_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_follow_up_risk CHECK (risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_follow_up_score_breakdown_object CHECK (jsonb_typeof(score_breakdown) = 'object'),
    CONSTRAINT ck_follow_up_recommended_action_object CHECK (jsonb_typeof(recommended_action) = 'object'),
    CONSTRAINT ck_follow_up_evidence_array CHECK (jsonb_typeof(evidence) = 'array')
);

CREATE UNIQUE INDEX uq_follow_up_active_ai_customer
    ON follow_up (organization_id, customer_id)
    WHERE origin = 'AI' AND status IN ('OPEN', 'IN_PROGRESS', 'SNOOZED');

CREATE INDEX ix_follow_up_today_queue
    ON follow_up (organization_id, status, due_at, priority DESC, id)
    WHERE status IN ('OPEN', 'IN_PROGRESS', 'SNOOZED');

CREATE INDEX ix_follow_up_owner_queue
    ON follow_up (organization_id, owner_member_id, status, due_at, priority DESC)
    WHERE status IN ('OPEN', 'IN_PROGRESS', 'SNOOZED');

-- -----------------------------------------------------------------------------
-- 动作、审批与工具执行
-- -----------------------------------------------------------------------------

CREATE TABLE tool_policy (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    tool_name           varchar(120) NOT NULL,
    enabled             boolean NOT NULL DEFAULT true,
    approval_mode       varchar(24) NOT NULL DEFAULT 'DEFAULT',
    max_batch_size      integer NOT NULL DEFAULT 1,
    rate_limit_per_hour integer,
    allowed_fields      text[] NOT NULL DEFAULT ARRAY[]::text[],
    config              jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_tool_policy_tool UNIQUE (organization_id, tool_name),
    CONSTRAINT uq_tool_policy_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT ck_tool_policy_approval CHECK (approval_mode IN ('DEFAULT', 'ALWAYS', 'NEVER', 'DENY')),
    CONSTRAINT ck_tool_policy_batch CHECK (max_batch_size > 0),
    CONSTRAINT ck_tool_policy_rate CHECK (rate_limit_per_hour IS NULL OR rate_limit_per_hour > 0),
    CONSTRAINT ck_tool_policy_config_object CHECK (jsonb_typeof(config) = 'object')
);

CREATE TABLE action_request (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    run_id              uuid NOT NULL,
    step_id             uuid,
    customer_id         uuid NOT NULL,
    follow_up_id        uuid,
    requested_by_member_id uuid,
    action_type         varchar(40) NOT NULL,
    risk_level          varchar(16) NOT NULL,
    status              varchar(32) NOT NULL DEFAULT 'PROPOSED',
    tool_name           varchar(120) NOT NULL,
    tool_version        varchar(40) NOT NULL,
    reason              text NOT NULL,
    payload             jsonb NOT NULL,
    payload_hash        char(64) NOT NULL,
    preview             jsonb NOT NULL DEFAULT '{}'::jsonb,
    requires_approval   boolean NOT NULL,
    policy_decision     varchar(24) NOT NULL,
    policy_snapshot     jsonb NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key     varchar(255) NOT NULL,
    expires_at          timestamptz,
    approved_at         timestamptz,
    execution_started_at timestamptz,
    completed_at        timestamptz,
    failure_code        varchar(80),
    failure_message     text,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_action_request_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uq_action_request_run_id UNIQUE (organization_id, run_id, id),
    CONSTRAINT uq_action_request_idempotency UNIQUE (organization_id, idempotency_key),
    CONSTRAINT fk_action_request_run
        FOREIGN KEY (organization_id, run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT fk_action_request_step
        FOREIGN KEY (organization_id, run_id, step_id)
        REFERENCES agent_step (organization_id, run_id, id),
    CONSTRAINT fk_action_request_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT fk_action_request_follow_up
        FOREIGN KEY (organization_id, customer_id, follow_up_id)
        REFERENCES follow_up (organization_id, customer_id, id),
    CONSTRAINT fk_action_request_requester
        FOREIGN KEY (organization_id, requested_by_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_action_request_type CHECK (action_type IN (
        'GENERATE_EMAIL_DRAFT', 'SEND_EMAIL', 'CREATE_INTERNAL_FOLLOW_UP',
        'CREATE_CRM_TASK', 'UPDATE_CRM_FIELD', 'SCHEDULE_MEETING', 'ADD_NOTE'
    )),
    CONSTRAINT ck_action_request_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_action_request_status CHECK (status IN (
        'PROPOSED', 'AWAITING_APPROVAL', 'APPROVED', 'REJECTED', 'EXPIRED',
        'EXECUTING', 'SUCCEEDED', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT ck_action_request_policy_decision CHECK (policy_decision IN ('ALLOW', 'REQUIRE_APPROVAL', 'DENY')),
    CONSTRAINT ck_action_request_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_action_request_preview_object CHECK (jsonb_typeof(preview) = 'object'),
    CONSTRAINT ck_action_request_policy_object CHECK (jsonb_typeof(policy_snapshot) = 'object'),
    CONSTRAINT ck_action_request_approval_consistency CHECK (
        (requires_approval = true AND policy_decision = 'REQUIRE_APPROVAL') OR
        (requires_approval = false AND policy_decision IN ('ALLOW', 'DENY'))
    ),
    CONSTRAINT ck_action_request_expiry CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX ix_action_request_run_status
    ON action_request (organization_id, run_id, status, created_at);

CREATE INDEX ix_action_request_customer_created
    ON action_request (organization_id, customer_id, created_at DESC);

CREATE INDEX ix_action_request_execution_queue
    ON action_request (status, approved_at, created_at)
    WHERE status = 'APPROVED';

CREATE TABLE approval (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    action_request_id   uuid NOT NULL,
    requested_by_member_id uuid,
    decided_by_member_id uuid,
    status              varchar(24) NOT NULL DEFAULT 'PENDING',
    request_reason      text NOT NULL,
    content_hash        char(64) NOT NULL,
    decision_comment    text,
    requested_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_at          timestamptz,
    expires_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_approval_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uq_approval_action UNIQUE (organization_id, action_request_id),
    CONSTRAINT fk_approval_action
        FOREIGN KEY (organization_id, action_request_id)
        REFERENCES action_request (organization_id, id),
    CONSTRAINT fk_approval_requester
        FOREIGN KEY (organization_id, requested_by_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT fk_approval_decider
        FOREIGN KEY (organization_id, decided_by_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_approval_decision_consistency CHECK (
        (status = 'PENDING' AND decided_at IS NULL AND decided_by_member_id IS NULL) OR
        (status IN ('APPROVED', 'REJECTED') AND decided_at IS NOT NULL AND decided_by_member_id IS NOT NULL) OR
        (status IN ('EXPIRED', 'CANCELLED'))
    ),
    CONSTRAINT ck_approval_expiry CHECK (expires_at IS NULL OR expires_at > requested_at)
);

CREATE INDEX ix_approval_pending_queue
    ON approval (organization_id, requested_at, id)
    WHERE status = 'PENDING';

CREATE INDEX ix_approval_decider_history
    ON approval (organization_id, decided_by_member_id, decided_at DESC)
    WHERE decided_by_member_id IS NOT NULL;

CREATE TABLE tool_invocation (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    action_request_id   uuid NOT NULL,
    run_id              uuid NOT NULL,
    step_id             uuid,
    connection_id       uuid,
    tool_name           varchar(120) NOT NULL,
    tool_version        varchar(40) NOT NULL,
    attempt_no          integer NOT NULL,
    idempotency_key     varchar(255) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'STARTED',
    request_payload     jsonb NOT NULL,
    response_payload    jsonb NOT NULL DEFAULT '{}'::jsonb,
    external_operation_id varchar(500),
    provider_request_id varchar(500),
    retryable           boolean NOT NULL DEFAULT false,
    error_code          varchar(80),
    error_message       text,
    latency_ms          bigint,
    started_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_tool_invocation_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uq_tool_invocation_attempt UNIQUE (organization_id, action_request_id, attempt_no),
    CONSTRAINT uq_tool_invocation_idempotent_attempt UNIQUE (organization_id, idempotency_key, attempt_no),
    CONSTRAINT fk_tool_invocation_action
        FOREIGN KEY (organization_id, run_id, action_request_id)
        REFERENCES action_request (organization_id, run_id, id),
    CONSTRAINT fk_tool_invocation_run
        FOREIGN KEY (organization_id, run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT fk_tool_invocation_step
        FOREIGN KEY (organization_id, run_id, step_id)
        REFERENCES agent_step (organization_id, run_id, id),
    CONSTRAINT fk_tool_invocation_connection
        FOREIGN KEY (organization_id, connection_id)
        REFERENCES integration_connection (organization_id, id),
    CONSTRAINT ck_tool_invocation_attempt CHECK (attempt_no > 0),
    CONSTRAINT ck_tool_invocation_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED')),
    CONSTRAINT ck_tool_invocation_request_object CHECK (jsonb_typeof(request_payload) = 'object'),
    CONSTRAINT ck_tool_invocation_response_object CHECK (jsonb_typeof(response_payload) = 'object'),
    CONSTRAINT ck_tool_invocation_latency CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE INDEX ix_tool_invocation_action_attempt
    ON tool_invocation (organization_id, action_request_id, attempt_no DESC);

CREATE INDEX ix_tool_invocation_unknown
    ON tool_invocation (organization_id, started_at)
    WHERE status = 'UNKNOWN';

-- -----------------------------------------------------------------------------
-- 用量、审计、事务发件箱与 HTTP 幂等
-- -----------------------------------------------------------------------------

CREATE TABLE usage_ledger (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    run_id              uuid,
    model_call_id       uuid,
    tool_invocation_id  uuid,
    usage_type          varchar(32) NOT NULL,
    provider            varchar(80),
    resource_name       varchar(160),
    quantity            numeric(20,6) NOT NULL,
    unit                varchar(32) NOT NULL,
    unit_cost           numeric(19,10),
    total_cost          numeric(19,8),
    currency            char(3),
    pricing_version     varchar(80),
    occurred_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    metadata            jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_usage_run
        FOREIGN KEY (organization_id, run_id)
        REFERENCES agent_run (organization_id, id),
    CONSTRAINT fk_usage_model_call
        FOREIGN KEY (organization_id, model_call_id)
        REFERENCES model_call (organization_id, id),
    CONSTRAINT fk_usage_tool_invocation
        FOREIGN KEY (organization_id, tool_invocation_id)
        REFERENCES tool_invocation (organization_id, id),
    CONSTRAINT ck_usage_type CHECK (usage_type IN ('MODEL_INPUT_TOKEN', 'MODEL_OUTPUT_TOKEN', 'MODEL_CACHED_TOKEN', 'TOOL_CALL', 'EMAIL_SENT')),
    CONSTRAINT ck_usage_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_usage_cost CHECK (
        (unit_cost IS NULL OR unit_cost >= 0) AND
        (total_cost IS NULL OR total_cost >= 0)
    ),
    CONSTRAINT ck_usage_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_usage_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX ix_usage_ledger_billing
    ON usage_ledger (organization_id, occurred_at, usage_type);

CREATE TABLE audit_event (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id     uuid NOT NULL REFERENCES organization(id),
    actor_type          varchar(24) NOT NULL,
    actor_member_id     uuid,
    actor_identifier    varchar(255),
    action              varchar(120) NOT NULL,
    target_type         varchar(80) NOT NULL,
    target_id           varchar(255) NOT NULL,
    result              varchar(24) NOT NULL,
    ip_address          inet,
    user_agent          varchar(1000),
    request_id          varchar(160),
    trace_id            varchar(64),
    before_snapshot     jsonb,
    after_snapshot      jsonb,
    metadata            jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_audit_actor_member
        FOREIGN KEY (organization_id, actor_member_id)
        REFERENCES organization_member (organization_id, id),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('USER', 'AGENT', 'SYSTEM', 'WEBHOOK')),
    CONSTRAINT ck_audit_result CHECK (result IN ('SUCCEEDED', 'FAILED', 'DENIED')),
    CONSTRAINT ck_audit_before_object CHECK (before_snapshot IS NULL OR jsonb_typeof(before_snapshot) = 'object'),
    CONSTRAINT ck_audit_after_object CHECK (after_snapshot IS NULL OR jsonb_typeof(after_snapshot) = 'object'),
    CONSTRAINT ck_audit_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX ix_audit_event_target
    ON audit_event (organization_id, target_type, target_id, occurred_at DESC);

CREATE INDEX ix_audit_event_actor
    ON audit_event (organization_id, actor_member_id, occurred_at DESC)
    WHERE actor_member_id IS NOT NULL;

CREATE INDEX ix_audit_event_retention
    ON audit_event (occurred_at);

CREATE TABLE outbox_event (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    aggregate_type      varchar(80) NOT NULL,
    aggregate_id        varchar(255) NOT NULL,
    event_type          varchar(120) NOT NULL,
    payload             jsonb NOT NULL,
    headers             jsonb NOT NULL DEFAULT '{}'::jsonb,
    status              varchar(24) NOT NULL DEFAULT 'PENDING',
    available_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    attempts            integer NOT NULL DEFAULT 0,
    locked_by           varchar(160),
    locked_until        timestamptz,
    last_error          text,
    published_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_outbox_headers_object CHECK (jsonb_typeof(headers) = 'object')
);

CREATE INDEX ix_outbox_delivery
    ON outbox_event (status, available_at, created_at)
    WHERE status IN ('PENDING', 'PUBLISHING');

CREATE INDEX ix_outbox_retention
    ON outbox_event (published_at)
    WHERE status = 'PUBLISHED';

CREATE TABLE idempotency_record (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid NOT NULL REFERENCES organization(id),
    idempotency_key     varchar(255) NOT NULL,
    request_method      varchar(10) NOT NULL,
    request_path        varchar(500) NOT NULL,
    request_hash        char(64) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'PROCESSING',
    response_status     integer,
    response_headers    jsonb NOT NULL DEFAULT '{}'::jsonb,
    response_body       jsonb,
    resource_type       varchar(80),
    resource_id         varchar(255),
    locked_until        timestamptz,
    expires_at          timestamptz NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at        timestamptz,
    CONSTRAINT uq_idempotency_key UNIQUE (organization_id, idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_idempotency_response_status CHECK (response_status IS NULL OR response_status BETWEEN 100 AND 599),
    CONSTRAINT ck_idempotency_headers_object CHECK (jsonb_typeof(response_headers) = 'object'),
    CONSTRAINT ck_idempotency_body_json CHECK (
        response_body IS NULL OR jsonb_typeof(response_body) IN ('object', 'array', 'string', 'number', 'boolean', 'null')
    )
);

CREATE INDEX ix_idempotency_expiry
    ON idempotency_record (expires_at);

-- -----------------------------------------------------------------------------
-- updated_at 自动更新时间触发器
-- version 字段由应用在乐观锁 UPDATE 语句中显式递增。
-- -----------------------------------------------------------------------------

CREATE TRIGGER trg_organization_updated_at
BEFORE UPDATE ON organization FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_app_user_updated_at
BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_organization_member_updated_at
BEFORE UPDATE ON organization_member FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_integration_connection_updated_at
BEFORE UPDATE ON integration_connection FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_integration_sync_cursor_updated_at
BEFORE UPDATE ON integration_sync_cursor FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_customer_updated_at
BEFORE UPDATE ON customer FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_customer_contact_updated_at
BEFORE UPDATE ON customer_contact FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_opportunity_updated_at
BEFORE UPDATE ON opportunity FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_config_updated_at
BEFORE UPDATE ON agent_config FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_run_updated_at
BEFORE UPDATE ON agent_run FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_follow_up_updated_at
BEFORE UPDATE ON follow_up FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tool_policy_updated_at
BEFORE UPDATE ON tool_policy FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_action_request_updated_at
BEFORE UPDATE ON action_request FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_approval_updated_at
BEFORE UPDATE ON approval FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- 便捷查询视图。API 查询这些视图时仍然必须应用组织过滤和权限校验。
-- -----------------------------------------------------------------------------

CREATE VIEW pending_approval_view AS
SELECT
    a.organization_id,
    a.id AS approval_id,
    a.action_request_id,
    a.requested_at,
    a.expires_at,
    ar.run_id,
    ar.customer_id,
    ar.action_type,
    ar.risk_level,
    ar.reason,
    ar.preview,
    ar.payload_hash,
    c.name AS customer_name
FROM approval a
JOIN action_request ar
  ON ar.organization_id = a.organization_id
 AND ar.id = a.action_request_id
JOIN customer c
  ON c.organization_id = ar.organization_id
 AND c.id = ar.customer_id
WHERE a.status = 'PENDING'
  AND ar.status = 'AWAITING_APPROVAL';

CREATE VIEW open_follow_up_view AS
SELECT
    f.organization_id,
    f.id AS follow_up_id,
    f.customer_id,
    c.name AS customer_name,
    f.owner_member_id,
    f.status,
    f.due_at,
    f.priority,
    f.intent_level,
    f.risk_level,
    f.reason,
    f.recommended_action_type,
    f.recommended_action
FROM follow_up f
JOIN customer c
  ON c.organization_id = f.organization_id
 AND c.id = f.customer_id
WHERE f.status IN ('OPEN', 'IN_PROGRESS', 'SNOOZED')
  AND c.deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- 数据字典注释
-- 注释保存在 PostgreSQL Catalog 中，可通过 psql \d+ 或数据库管理工具查看。
-- -----------------------------------------------------------------------------

-- 身份与租户
COMMENT ON TABLE organization IS '租户组织，是数据隔离、订阅、配置和权限控制的顶层边界。';
COMMENT ON COLUMN organization.slug IS '组织在人类可读 URL 中使用的唯一短标识。';
COMMENT ON COLUMN organization.timezone IS 'IANA 时区名称，用于计算组织业务日、定时任务和界面时间。';
COMMENT ON COLUMN organization.settings IS '组织级可扩展配置；需要查询和约束的核心字段不得只存放在此 JSON 中。';
COMMENT ON COLUMN organization.version IS '应用层乐观锁版本号，更新记录时由应用显式递增。';

COMMENT ON TABLE app_user IS '平台级用户账户；用户可通过组织成员关系加入多个租户。';
COMMENT ON COLUMN app_user.auth_subject IS '外部身份提供商中的稳定用户标识，不使用邮箱作为认证主键。';
COMMENT ON COLUMN app_user.status IS '账户状态：INVITED 待激活、ACTIVE 正常、DISABLED 禁用。';

COMMENT ON TABLE organization_member IS '用户与组织之间的成员关系，保存租户内角色和附加权限。';
COMMENT ON COLUMN organization_member.role IS '组织内基础角色：OWNER、ADMIN、MANAGER、SALES 或 VIEWER。';
COMMENT ON COLUMN organization_member.permissions IS '角色之外的附加权限列表；最终授权仍由服务端策略计算。';

-- 外部集成
COMMENT ON TABLE integration_connection IS '组织连接的邮箱、CRM 或日历账户及其健康状态。';
COMMENT ON COLUMN integration_connection.credential_ref IS 'Secret Manager 中的凭证引用；禁止在此存储 OAuth refresh token 或 API Key 明文。';
COMMENT ON COLUMN integration_connection.status IS '连接状态：PENDING、ACTIVE、REAUTH_REQUIRED、ERROR 或 DISABLED。';
COMMENT ON COLUMN integration_connection.settings IS '供应商非敏感配置，例如同步范围和默认发件身份。';
COMMENT ON COLUMN integration_connection.last_sync_at IS '该连接最近一次成功完成同步的时间。';

COMMENT ON TABLE integration_sync_cursor IS '外部资源增量同步的游标、水位线和 Worker 租约。';
COMMENT ON COLUMN integration_sync_cursor.cursor_value IS '供应商返回的不透明增量游标，应用不得解析其内部格式。';
COMMENT ON COLUMN integration_sync_cursor.watermark_at IS '基于时间增量同步时已经处理到的事件时间。';
COMMENT ON COLUMN integration_sync_cursor.lease_owner IS '当前持有同步任务租约的 Worker 标识。';
COMMENT ON COLUMN integration_sync_cursor.lease_expires_at IS '同步租约到期时间，Worker 崩溃后其他实例可在到期后接管。';

-- 销售业务数据
COMMENT ON TABLE customer IS '客户公司或销售账户，是联系人、商机、互动和跟进建议的归属主体。';
COMMENT ON COLUMN customer.stage IS '客户当前销售阶段，由业务确认后的事实值，不等同于模型建议值。';
COMMENT ON COLUMN customer.last_interaction_at IS '客户最近一次标准化互动时间，用于筛选和规则评分。';
COMMENT ON COLUMN customer.next_follow_up_at IS '客户下一次计划跟进时间，便于 Today Queue 快速查询。';
COMMENT ON COLUMN customer.attributes IS '导入字段或行业扩展属性；稳定业务字段应提升为标准列。';
COMMENT ON COLUMN customer.deleted_at IS '软删除时间；非空记录默认不出现在业务查询中。';

COMMENT ON TABLE customer_contact IS '客户下的自然人联系人；复合外键保证引用时与客户归属一致。';
COMMENT ON COLUMN customer_contact.is_primary IS '是否为该客户的主联系人；每个未删除客户最多一条主联系人。';
COMMENT ON COLUMN customer_contact.external_id IS '联系人在导入源或外部 CRM 中的标识。';

COMMENT ON TABLE opportunity IS '客户名下的销售商机，保存金额、阶段、赢单概率和预计关闭日期。';
COMMENT ON COLUMN opportunity.amount IS '商机金额，精度为 numeric(19,4)，币种由 currency 字段确定。';
COMMENT ON COLUMN opportunity.probability IS '业务系统中的赢单概率，范围为 0-100。';
COMMENT ON COLUMN opportunity.status IS '商机状态：OPEN、WON、LOST 或 ARCHIVED。';

COMMENT ON TABLE interaction IS '客户时间线中的追加式互动事件，例如邮件、通话、会议、备注和 CRM 更新。';
COMMENT ON COLUMN interaction.occurred_at IS '互动实际发生时间，不等同于该记录写入数据库的时间。';
COMMENT ON COLUMN interaction.body_preview IS '用于列表和模型预筛的受限长度正文摘要。';
COMMENT ON COLUMN interaction.source_payload IS '外部供应商原始事件快照，用于排障和重新映射；不得包含访问凭证。';
COMMENT ON COLUMN interaction.external_id IS '外部事件唯一标识，与 organization_id 和 source 共同用于去重。';

-- Agent 配置与运行时
COMMENT ON TABLE agent_config IS '组织级 Agent 配置，保存调度、模型档案和 Prompt/Schema 版本。';
COMMENT ON COLUMN agent_config.prompt_version IS '本次配置使用的系统 Prompt 版本，便于复现和评测。';
COMMENT ON COLUMN agent_config.schema_version IS '模型结构化输出 Schema 版本。';
COMMENT ON COLUMN agent_config.config IS 'Agent 规则参数，例如过期天数和评分权重；配置内容需要版本化。';

COMMENT ON TABLE agent_run IS '一次 Agent 编排执行的当前状态快照；完整历史记录在 agent_run_event 和 agent_step。';
COMMENT ON COLUMN agent_run.deduplication_key IS '业务去重键，防止同一组织在同一业务日重复创建相同 Run。';
COMMENT ON COLUMN agent_run.scope IS '本次 Run 的处理范围，例如客户、负责人和业务日期过滤条件。';
COMMENT ON COLUMN agent_run.input_snapshot IS 'Run 启动时的配置和输入快照，用于审计与复现。';
COMMENT ON COLUMN agent_run.output_summary IS 'Run 完成后的业务汇总，不替代逐客户 Step 和事件。';
COMMENT ON COLUMN agent_run.last_event_sequence IS '该 Run 已分配的最大事件序号，用于原子生成 SSE 回放顺序。';
COMMENT ON COLUMN agent_run.lease_owner IS '当前执行该 Run 的 Worker 标识。';
COMMENT ON COLUMN agent_run.lease_expires_at IS 'Worker 执行租约到期时间；到期后允许安全接管。';
COMMENT ON COLUMN agent_run.heartbeat_at IS 'Worker 最近一次续约或心跳时间，用于识别失联任务。';
COMMENT ON COLUMN agent_run.version IS '并发审批、取消和 Worker 更新使用的乐观锁版本号。';

COMMENT ON TABLE agent_step IS 'Agent Run 中有业务意义的追加式步骤记录，用于解释执行过程和定位失败。';
COMMENT ON COLUMN agent_step.sequence_no IS 'Step 在单个 Run 内的严格递增顺序号。';
COMMENT ON COLUMN agent_step.step_type IS '步骤类型，例如模型调用、决策、审批等待、工具调用或错误。';
COMMENT ON COLUMN agent_step.input_snapshot IS '执行该步骤时经过脱敏的输入快照。';
COMMENT ON COLUMN agent_step.output_snapshot IS '步骤输出摘要或结构化结果；大型原始内容不应无界存入。';

COMMENT ON TABLE agent_run_event IS '面向 SSE 和状态历史回放的 Run 追加式事件流。';
COMMENT ON COLUMN agent_run_event.id IS '全局事件 ID，仅用于数据库定位；客户端顺序以 Run 内 sequence_no 为准。';
COMMENT ON COLUMN agent_run_event.sequence_no IS '事件在单个 Run 内的单调递增序号，支持断线续传和缺口检测。';
COMMENT ON COLUMN agent_run_event.payload IS '供前端安全展示的事件数据，不包含凭证、完整邮件正文或未脱敏 Prompt。';

COMMENT ON TABLE model_call IS '一次实际模型供应商调用，记录模型、版本、用量、成本和脱敏快照。';
COMMENT ON COLUMN model_call.purpose IS '模型调用目的：销售分析、邮件草稿或摘要。';
COMMENT ON COLUMN model_call.provider_request_id IS '模型供应商返回的请求 ID，用于对账和故障排查。';
COMMENT ON COLUMN model_call.pricing_version IS '估算历史成本时使用的价格表版本，避免按当前价格重算。';
COMMENT ON COLUMN model_call.input_hash IS '规范化且脱敏后的模型输入 SHA-256 十六进制摘要。';
COMMENT ON COLUMN model_call.output_hash IS '模型原始输出 SHA-256 十六进制摘要。';
COMMENT ON COLUMN model_call.input_snapshot IS '可选的脱敏输入快照；生产环境应遵循最小化和保留策略。';
COMMENT ON COLUMN model_call.output_snapshot IS '解析后的结构化输出或脱敏响应摘要。';

-- Follow-up 工作队列
COMMENT ON TABLE follow_up IS '客户跟进建议和 Today Queue 的持久化业务记录。';
COMMENT ON COLUMN follow_up.priority IS '最终可解释优先级，范围 0-100，由模型分数与确定性规则共同计算。';
COMMENT ON COLUMN follow_up.ai_score IS '模型给出的原始分数，不能直接作为最终业务优先级。';
COMMENT ON COLUMN follow_up.score_version IS '确定性评分算法版本。';
COMMENT ON COLUMN follow_up.score_breakdown IS '逾期、金额、近期信号等评分分项，供解释和调试。';
COMMENT ON COLUMN follow_up.recommended_action IS '模型建议动作的结构化快照，真正执行前需转换为 ActionRequest。';
COMMENT ON COLUMN follow_up.evidence IS '支持该建议的客户互动证据列表，禁止存放模型虚构事实。';

-- 动作、审批与工具执行
COMMENT ON TABLE tool_policy IS '组织对已在代码中注册工具的启用、审批、批量和限流覆盖策略。';
COMMENT ON COLUMN tool_policy.approval_mode IS '审批覆盖模式：DEFAULT、ALWAYS、NEVER 或 DENY。';
COMMENT ON COLUMN tool_policy.allowed_fields IS '更新外部系统时允许写入的字段白名单。';
COMMENT ON COLUMN tool_policy.config IS '工具非敏感策略配置；不得通过此字段加载可执行代码。';

COMMENT ON TABLE action_request IS '模型或业务规则提出的持久化副作用动作，是审批和工具执行的锚点。';
COMMENT ON COLUMN action_request.status IS '动作状态；APPROVED 只代表允许执行，SUCCEEDED 才代表外部动作成功。';
COMMENT ON COLUMN action_request.payload IS '工具执行使用的规范化最终参数，批准后不得静默修改。';
COMMENT ON COLUMN action_request.payload_hash IS '规范化动作 payload 的 SHA-256 十六进制摘要，用于确认执行内容与批准内容一致。';
COMMENT ON COLUMN action_request.preview IS '面向审批人的安全预览，例如邮件收件人、主题和正文。';
COMMENT ON COLUMN action_request.policy_snapshot IS '创建动作时的策略决策快照，用于解释为何允许、拒绝或要求审批。';
COMMENT ON COLUMN action_request.idempotency_key IS '动作级业务幂等键，阻止 Worker 重放产生重复外部副作用。';
COMMENT ON COLUMN action_request.expires_at IS '动作到期时间，到期后旧内容不得继续批准或执行。';
COMMENT ON COLUMN action_request.version IS '审批、取消和执行竞争时使用的乐观锁版本号。';

COMMENT ON TABLE approval IS '一个 ActionRequest 的人工审批事实；做出决定后不得覆盖为另一决定。';
COMMENT ON COLUMN approval.status IS '审批状态：PENDING、APPROVED、REJECTED、EXPIRED 或 CANCELLED。';
COMMENT ON COLUMN approval.content_hash IS '审批人实际看到内容的 SHA-256 摘要，必须与 ActionRequest payload_hash 对应。';
COMMENT ON COLUMN approval.decided_by_member_id IS '执行批准或拒绝决策的组织成员。';
COMMENT ON COLUMN approval.version IS '并发审批使用的乐观锁版本号，保证一次性决策。';

COMMENT ON TABLE tool_invocation IS '外部工具的一次执行尝试，包括成功、失败和结果未知状态。';
COMMENT ON COLUMN tool_invocation.attempt_no IS '同一 ActionRequest 的执行尝试序号，从 1 开始。';
COMMENT ON COLUMN tool_invocation.status IS '执行状态；UNKNOWN 表示请求结果不确定，必须先对账而不能盲目重试。';
COMMENT ON COLUMN tool_invocation.idempotency_key IS '向工具适配器和支持幂等的外部供应商透传的执行幂等键。';
COMMENT ON COLUMN tool_invocation.external_operation_id IS '外部系统中的操作 ID，用于查询结果和对账。';
COMMENT ON COLUMN tool_invocation.response_payload IS '外部响应的脱敏结构化快照。';
COMMENT ON COLUMN tool_invocation.retryable IS '当前失败是否符合工具固定重试策略，不代表前端可以直接重试。';

-- 用量、审计与可靠消息
COMMENT ON TABLE usage_ledger IS '组织级追加式用量和成本台账，用于配额、账单和历史对账。';
COMMENT ON COLUMN usage_ledger.quantity IS '规范化使用数量，例如 Token 数、工具调用数或邮件数。';
COMMENT ON COLUMN usage_ledger.total_cost IS '按当时价格版本估算或结算的总成本。';
COMMENT ON COLUMN usage_ledger.pricing_version IS '产生该成本时使用的价格表版本。';

COMMENT ON TABLE audit_event IS '安全和业务操作的追加式审计事件，回答谁在何时对什么执行了什么。';
COMMENT ON COLUMN audit_event.actor_type IS '操作主体类型：用户、Agent、系统或 Webhook。';
COMMENT ON COLUMN audit_event.target_id IS '被操作资源 ID 的文本形式，支持不同主键类型。';
COMMENT ON COLUMN audit_event.before_snapshot IS '操作前的脱敏快照；仅在审计确有需要时保存。';
COMMENT ON COLUMN audit_event.after_snapshot IS '操作后的脱敏快照；不得包含访问凭证。';
COMMENT ON COLUMN audit_event.trace_id IS '关联可观测性 Trace 的标识。';

COMMENT ON TABLE outbox_event IS '与业务事务原子写入的待发布事件，保证事务提交后可靠投递。';
COMMENT ON COLUMN outbox_event.status IS '投递状态：PENDING、PUBLISHING、PUBLISHED 或 DEAD。';
COMMENT ON COLUMN outbox_event.available_at IS '事件允许下一次投递的时间，用于退避重试。';
COMMENT ON COLUMN outbox_event.locked_until IS '发布 Worker 的短期租约到期时间。';
COMMENT ON COLUMN outbox_event.payload IS '事件业务载荷；必须保持向后兼容并排除敏感数据。';

COMMENT ON TABLE idempotency_record IS 'HTTP 写请求的幂等记录，用于安全返回首次请求结果并防止重复提交。';
COMMENT ON COLUMN idempotency_record.request_hash IS '规范化请求内容 SHA-256 摘要，用于检测同一幂等键被不同请求复用。';
COMMENT ON COLUMN idempotency_record.response_body IS '首次请求的可重放 JSON 响应；不得缓存不应长期保存的敏感正文。';
COMMENT ON COLUMN idempotency_record.locked_until IS 'PROCESSING 记录的处理租约，用于进程异常后的恢复判断。';
COMMENT ON COLUMN idempotency_record.expires_at IS '幂等记录保留截止时间，必须覆盖最长客户端和服务端重试窗口。';

-- 查询视图
COMMENT ON VIEW pending_approval_view IS '待审批动作的读模型，组合审批、动作和客户摘要；查询时仍必须过滤 organization_id。';
COMMENT ON VIEW open_follow_up_view IS '未完成跟进队列的读模型；查询时仍必须过滤 organization_id。';
