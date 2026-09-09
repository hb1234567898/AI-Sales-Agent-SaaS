ALTER TABLE agent_run
    DROP CONSTRAINT ck_agent_run_trigger;

ALTER TABLE agent_run
    ADD CONSTRAINT ck_agent_run_trigger
    CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'WEBHOOK', 'RETRY', 'MCP_ASSISTANT'));
