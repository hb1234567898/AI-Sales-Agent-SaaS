ALTER TABLE opportunity ADD COLUMN stage_changed_at timestamptz;
UPDATE opportunity SET stage_changed_at = created_at;
ALTER TABLE opportunity ALTER COLUMN stage_changed_at SET DEFAULT clock_timestamp();
ALTER TABLE opportunity ALTER COLUMN stage_changed_at SET NOT NULL;
ALTER TABLE opportunity ADD COLUMN close_reason varchar(1000);

CREATE TABLE opportunity_event (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    opportunity_id uuid NOT NULL,
    actor_member_id uuid NOT NULL,
    from_stage varchar(32),
    to_stage varchar(32) NOT NULL,
    summary varchar(1500) NOT NULL,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (organization_id, opportunity_id) REFERENCES opportunity (organization_id, id),
    FOREIGN KEY (organization_id, actor_member_id) REFERENCES organization_member (organization_id, id)
);
CREATE INDEX ix_opportunity_event_history ON opportunity_event (organization_id, opportunity_id, created_at DESC);
