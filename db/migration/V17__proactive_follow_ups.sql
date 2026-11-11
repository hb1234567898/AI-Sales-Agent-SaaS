ALTER TABLE follow_up DROP CONSTRAINT ck_follow_up_origin;
ALTER TABLE follow_up ADD CONSTRAINT ck_follow_up_origin CHECK (origin IN ('AI', 'MANUAL', 'IMPORT', 'RULE'));

CREATE TABLE follow_up_rule (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organization(id),
    member_id uuid NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    inactive_days integer NOT NULL DEFAULT 7 CHECK (inactive_days BETWEEN 1 AND 180),
    run_hour integer NOT NULL DEFAULT 9 CHECK (run_hour BETWEEN 0 AND 23),
    max_customers integer NOT NULL DEFAULT 5 CHECK (max_customers BETWEEN 1 AND 20),
    auto_analyze boolean NOT NULL DEFAULT false,
    reminder_minutes integer NOT NULL DEFAULT 30 CHECK (reminder_minutes BETWEEN 0 AND 10080),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, member_id),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, member_id) REFERENCES organization_member (organization_id, id)
);

CREATE TABLE follow_up_automation_run (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organization(id),
    rule_id uuid NOT NULL,
    member_id uuid NOT NULL,
    business_date date NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    created_count integer NOT NULL DEFAULT 0,
    agent_run_id uuid,
    error_message varchar(1000),
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, rule_id, business_date),
    FOREIGN KEY (organization_id, rule_id) REFERENCES follow_up_rule (organization_id, id),
    FOREIGN KEY (organization_id, member_id) REFERENCES organization_member (organization_id, id),
    FOREIGN KEY (organization_id, agent_run_id) REFERENCES agent_run (organization_id, id)
);
CREATE INDEX ix_follow_up_automation_queue ON follow_up_automation_run (created_at) WHERE status = 'QUEUED';
ALTER TABLE follow_up ADD COLUMN automation_run_id uuid;
ALTER TABLE follow_up ADD CONSTRAINT fk_follow_up_automation_run
    FOREIGN KEY (organization_id, automation_run_id) REFERENCES follow_up_automation_run (organization_id, id);
CREATE INDEX ix_follow_up_automation_tasks ON follow_up (organization_id, automation_run_id);
CREATE UNIQUE INDEX uq_follow_up_active_rule_customer ON follow_up (organization_id, customer_id)
    WHERE origin = 'RULE' AND status IN ('OPEN', 'IN_PROGRESS', 'SNOOZED');

CREATE TABLE follow_up_reminder (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organization(id),
    follow_up_id uuid NOT NULL,
    member_id uuid NOT NULL,
    kind varchar(24) NOT NULL CHECK (kind IN ('DUE_SOON', 'OVERDUE')),
    scheduled_for timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    read_at timestamptz,
    UNIQUE (organization_id, follow_up_id, member_id, kind, scheduled_for),
    FOREIGN KEY (organization_id, follow_up_id) REFERENCES follow_up (organization_id, id),
    FOREIGN KEY (organization_id, member_id) REFERENCES organization_member (organization_id, id)
);
CREATE INDEX ix_follow_up_reminder_inbox ON follow_up_reminder (organization_id, member_id, created_at DESC)
    WHERE read_at IS NULL;
