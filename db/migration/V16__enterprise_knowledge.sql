CREATE TABLE knowledge_document (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organization(id),
    uploaded_by_member_id uuid NOT NULL,
    file_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    category varchar(40) NOT NULL DEFAULT 'OTHER',
    visibility varchar(20) NOT NULL DEFAULT 'TEAM',
    status varchar(20) NOT NULL DEFAULT 'PROCESSING',
    enabled boolean NOT NULL DEFAULT true,
    chunk_count integer NOT NULL DEFAULT 0,
    character_count integer NOT NULL DEFAULT 0,
    error_message varchar(1000),
    revision bigint NOT NULL DEFAULT 1,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, uploaded_by_member_id) REFERENCES organization_member (organization_id, id),
    FOREIGN KEY (organization_id, file_id) REFERENCES uploaded_file (organization_id, id),
    CHECK (category IN ('PRODUCT', 'PRICING', 'PLAYBOOK', 'CASE_STUDY', 'POLICY', 'OTHER')),
    CHECK (visibility IN ('TEAM', 'PRIVATE')),
    CHECK (status IN ('PROCESSING', 'READY', 'FAILED', 'DELETED')),
    CHECK (length(btrim(title)) > 0)
);
CREATE INDEX ix_knowledge_document_org ON knowledge_document (organization_id, updated_at DESC) WHERE status <> 'DELETED';

CREATE TABLE knowledge_chunk (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    document_id uuid NOT NULL,
    revision bigint NOT NULL,
    chunk_index integer NOT NULL,
    page_number integer,
    content text NOT NULL,
    search_terms text NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', search_terms)) STORED,
    FOREIGN KEY (organization_id, document_id) REFERENCES knowledge_document (organization_id, id),
    UNIQUE (organization_id, document_id, chunk_index),
    CHECK (chunk_index >= 0 AND length(content) > 0)
);
CREATE INDEX ix_knowledge_chunk_document ON knowledge_chunk (organization_id, document_id);
CREATE INDEX ix_knowledge_chunk_search ON knowledge_chunk USING gin (search_vector);

ALTER TABLE model_call DROP CONSTRAINT ck_model_call_purpose;
ALTER TABLE model_call ADD CONSTRAINT ck_model_call_purpose
    CHECK (purpose IN ('SALES_ANALYSIS', 'EMAIL_DRAFT', 'SUMMARY', 'KNOWLEDGE_QA'));
