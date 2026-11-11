CREATE TABLE mail_account (
    organization_id uuid PRIMARY KEY REFERENCES organization(id),
    host varchar(255) NOT NULL, port integer NOT NULL CHECK (port BETWEEN 1 AND 65535),
    username varchar(320) NOT NULL, encrypted_password text NOT NULL,
    ssl boolean NOT NULL DEFAULT true, folder varchar(120) NOT NULL DEFAULT 'INBOX',
    enabled boolean NOT NULL DEFAULT false, version bigint NOT NULL DEFAULT 0,
    uid_validity bigint NOT NULL DEFAULT 0, last_uid bigint NOT NULL DEFAULT 0,
    last_sync_at timestamptz, last_error varchar(500),
    lease_token uuid, lease_until timestamptz, updated_at timestamptz NOT NULL,
    next_sync_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE mail_message (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id),
    customer_id uuid, owner_member_id uuid, action_request_id uuid, interaction_id uuid,
    thread_id uuid NOT NULL, parent_id uuid,
    direction varchar(12) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    status varchar(24) NOT NULL CHECK (status IN ('SENDING', 'SENT', 'UNKNOWN', 'FAILED', 'RECEIVED', 'UNMATCHED', 'HANDLED', 'REPLIED', 'IGNORED')),
    message_id varchar(500) NOT NULL, from_address varchar(320) NOT NULL,
    recipients jsonb NOT NULL DEFAULT '[]', subject varchar(500) NOT NULL,
    body_text text NOT NULL, attachment_names jsonb NOT NULL DEFAULT '[]',
    references_header text NOT NULL DEFAULT '',
    occurred_at timestamptz NOT NULL, created_at timestamptz NOT NULL,
    reply_approval_id uuid, version bigint NOT NULL DEFAULT 0,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, direction, message_id),
    UNIQUE (organization_id, action_request_id),
    FOREIGN KEY (organization_id, customer_id) REFERENCES customer(organization_id, id),
    FOREIGN KEY (organization_id, owner_member_id) REFERENCES organization_member(organization_id, id),
    FOREIGN KEY (organization_id, action_request_id) REFERENCES action_request(organization_id, id),
    FOREIGN KEY (organization_id, interaction_id) REFERENCES interaction(organization_id, id),
    FOREIGN KEY (organization_id, parent_id) REFERENCES mail_message(organization_id, id),
    FOREIGN KEY (organization_id, reply_approval_id) REFERENCES approval(organization_id, id),
    CHECK (jsonb_typeof(recipients) = 'array'),
    CHECK (jsonb_typeof(attachment_names) = 'array')
);
CREATE INDEX ix_mail_inbox ON mail_message (organization_id, direction, status, occurred_at DESC);
CREATE INDEX ix_mail_thread ON mail_message (organization_id, thread_id, occurred_at);
ALTER TABLE follow_up ADD COLUMN mail_message_id uuid;
ALTER TABLE follow_up ADD CONSTRAINT fk_follow_up_mail_message FOREIGN KEY (organization_id, mail_message_id) REFERENCES mail_message(organization_id, id);
CREATE UNIQUE INDEX uq_follow_up_mail_message ON follow_up (organization_id, mail_message_id) WHERE mail_message_id IS NOT NULL;
