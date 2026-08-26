-- 保存聊天记录的 AI 分析结果。每次重新分析都会新增版本，避免覆盖历史判断。
CREATE TABLE interaction_ai_analysis (
    id                      uuid PRIMARY KEY,
    organization_id         uuid NOT NULL REFERENCES organization(id),
    customer_id             uuid NOT NULL,
    interaction_id          uuid NOT NULL,
    analysis_version        integer NOT NULL,
    status                  varchar(16) NOT NULL DEFAULT 'DRAFT',
    model_provider          varchar(40) NOT NULL,
    model_name              varchar(120) NOT NULL,
    prompt_version          varchar(40) NOT NULL,
    summary                 text NOT NULL,
    intent_score            smallint NOT NULL,
    intent_level            varchar(16) NOT NULL,
    sentiment               varchar(16) NOT NULL,
    needs                   jsonb NOT NULL DEFAULT '[]'::jsonb,
    pain_points             jsonb NOT NULL DEFAULT '[]'::jsonb,
    objections              jsonb NOT NULL DEFAULT '[]'::jsonb,
    risks                   jsonb NOT NULL DEFAULT '[]'::jsonb,
    recommended_actions     jsonb NOT NULL DEFAULT '[]'::jsonb,
    suggested_next_action   varchar(500),
    budget_signal           varchar(500),
    timeline_signal         varchar(500),
    decision_maker_signal   varchar(500),
    evidence                jsonb NOT NULL DEFAULT '[]'::jsonb,
    analyzed_at             timestamptz NOT NULL,
    applied_at              timestamptz,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT fk_interaction_ai_analysis_customer
        FOREIGN KEY (organization_id, customer_id)
        REFERENCES customer (organization_id, id),
    CONSTRAINT fk_interaction_ai_analysis_interaction
        FOREIGN KEY (organization_id, interaction_id)
        REFERENCES interaction (organization_id, id),
    CONSTRAINT uq_interaction_ai_analysis_version
        UNIQUE (organization_id, interaction_id, analysis_version),
    CONSTRAINT ck_interaction_ai_analysis_status
        CHECK (status IN ('DRAFT', 'APPLIED')),
    CONSTRAINT ck_interaction_ai_analysis_intent_score
        CHECK (intent_score BETWEEN 0 AND 100),
    CONSTRAINT ck_interaction_ai_analysis_intent_level
        CHECK (intent_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_interaction_ai_analysis_sentiment
        CHECK (sentiment IN ('NEGATIVE', 'NEUTRAL', 'POSITIVE', 'MIXED')),
    CONSTRAINT ck_interaction_ai_analysis_needs_array CHECK (jsonb_typeof(needs) = 'array'),
    CONSTRAINT ck_interaction_ai_analysis_pain_points_array CHECK (jsonb_typeof(pain_points) = 'array'),
    CONSTRAINT ck_interaction_ai_analysis_objections_array CHECK (jsonb_typeof(objections) = 'array'),
    CONSTRAINT ck_interaction_ai_analysis_risks_array CHECK (jsonb_typeof(risks) = 'array'),
    CONSTRAINT ck_interaction_ai_analysis_actions_array CHECK (jsonb_typeof(recommended_actions) = 'array'),
    CONSTRAINT ck_interaction_ai_analysis_evidence_array CHECK (jsonb_typeof(evidence) = 'array')
);

-- 支持按客户一次读取每条聊天的最新分析版本。
CREATE INDEX ix_interaction_ai_analysis_customer_latest
    ON interaction_ai_analysis (organization_id, customer_id, interaction_id, analysis_version DESC);

-- 支持查询尚未经过销售确认的 AI 建议。
CREATE INDEX ix_interaction_ai_analysis_draft
    ON interaction_ai_analysis (organization_id, status, analyzed_at DESC)
    WHERE status = 'DRAFT';

COMMENT ON TABLE interaction_ai_analysis IS '聊天互动的版本化 AI 分析结果；DRAFT 只表示模型建议，APPLIED 表示销售已确认回写。';
COMMENT ON COLUMN interaction_ai_analysis.analysis_version IS '同一聊天记录的分析版本，从 1 开始递增。';
COMMENT ON COLUMN interaction_ai_analysis.prompt_version IS '生成本结果的提示词版本，用于复现、回归测试和审计。';
COMMENT ON COLUMN interaction_ai_analysis.evidence IS '支持分析结论的聊天原文短句，不保存模型臆测的证据。';
COMMENT ON COLUMN interaction_ai_analysis.applied_at IS '销售确认并回写客户评分与下一步动作的时间。';
