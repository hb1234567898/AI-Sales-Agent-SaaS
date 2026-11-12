CREATE TABLE sales_product (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id),
    sku varchar(40) NOT NULL, name varchar(200) NOT NULL, unit varchar(30) NOT NULL,
    list_price numeric(14,2) NOT NULL CHECK (list_price > 0),
    tax_rate numeric(5,2) NOT NULL CHECK (tax_rate BETWEEN 0 AND 100),
    active boolean NOT NULL DEFAULT true, description varchar(2000) NOT NULL DEFAULT '',
    version bigint NOT NULL DEFAULT 0, updated_at timestamptz NOT NULL,
    UNIQUE (organization_id, id), UNIQUE (organization_id, sku)
);
CREATE TABLE sales_price_book (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id),
    name varchar(200) NOT NULL, currency char(3) NOT NULL DEFAULT 'CNY' CHECK (currency = 'CNY'),
    valid_from date NOT NULL, valid_until date NOT NULL CHECK (valid_until >= valid_from),
    active boolean NOT NULL DEFAULT true, version bigint NOT NULL DEFAULT 0, updated_at timestamptz NOT NULL,
    UNIQUE (organization_id, id)
);
CREATE TABLE sales_price_entry (
    organization_id uuid NOT NULL, price_book_id uuid NOT NULL, product_id uuid NOT NULL,
    unit_price numeric(14,2) NOT NULL CHECK (unit_price > 0),
    PRIMARY KEY (organization_id, price_book_id, product_id),
    FOREIGN KEY (organization_id, price_book_id) REFERENCES sales_price_book(organization_id, id),
    FOREIGN KEY (organization_id, product_id) REFERENCES sales_product(organization_id, id)
);
CREATE TABLE sales_quote (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id),
    number varchar(40) NOT NULL, opportunity_id uuid NOT NULL, customer_id uuid NOT NULL,
    current_revision integer NOT NULL DEFAULT 1 CHECK (current_revision > 0),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ISSUED','ACCEPTED','REJECTED','CANCELLED')),
    version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    UNIQUE (organization_id, id), UNIQUE (organization_id, number),
    FOREIGN KEY (organization_id, customer_id, opportunity_id) REFERENCES opportunity(organization_id, customer_id, id)
);
CREATE TABLE sales_quote_version (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL, quote_id uuid NOT NULL, revision integer NOT NULL,
    title varchar(200) NOT NULL, price_book_id uuid, price_book_name varchar(200) NOT NULL,
    customer_name varchar(255) NOT NULL, seller_name varchar(255) NOT NULL, valid_until date NOT NULL, terms varchar(5000) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'CNY' CHECK (currency = 'CNY'),
    subtotal numeric(14,2) NOT NULL CHECK (subtotal >= 0),
    tax numeric(14,2) NOT NULL CHECK (tax >= 0), total numeric(14,2) NOT NULL CHECK (total > 0 AND total = subtotal + tax),
    created_by uuid NOT NULL, created_at timestamptz NOT NULL,
    file_id uuid, approval_id uuid,
    UNIQUE (organization_id, id), UNIQUE (organization_id, quote_id, id), UNIQUE (organization_id, quote_id, revision),
    FOREIGN KEY (organization_id, quote_id) REFERENCES sales_quote(organization_id, id),
    FOREIGN KEY (organization_id, price_book_id) REFERENCES sales_price_book(organization_id, id),
    FOREIGN KEY (organization_id, created_by) REFERENCES organization_member(organization_id, id),
    FOREIGN KEY (organization_id, file_id) REFERENCES uploaded_file(organization_id, id),
    FOREIGN KEY (organization_id, approval_id) REFERENCES approval(organization_id, id)
);
ALTER TABLE sales_quote ADD CONSTRAINT fk_sales_quote_current_revision
    FOREIGN KEY (organization_id, id, current_revision)
    REFERENCES sales_quote_version(organization_id, quote_id, revision) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE sales_quote_line (
    organization_id uuid NOT NULL, quote_version_id uuid NOT NULL, position integer NOT NULL CHECK (position > 0),
    product_id uuid NOT NULL, sku varchar(40) NOT NULL, product_name varchar(200) NOT NULL, unit varchar(30) NOT NULL,
    quantity numeric(12,3) NOT NULL CHECK (quantity > 0), unit_price numeric(14,2) NOT NULL CHECK (unit_price > 0),
    discount_percent numeric(5,2) NOT NULL CHECK (discount_percent >= 0 AND discount_percent < 100),
    tax_rate numeric(5,2) NOT NULL CHECK (tax_rate BETWEEN 0 AND 100),
    subtotal numeric(14,2) NOT NULL CHECK (subtotal >= 0), tax numeric(14,2) NOT NULL CHECK (tax >= 0),
    total numeric(14,2) NOT NULL CHECK (total = subtotal + tax),
    PRIMARY KEY (organization_id, quote_version_id, position),
    FOREIGN KEY (organization_id, quote_version_id) REFERENCES sales_quote_version(organization_id, id),
    FOREIGN KEY (organization_id, product_id) REFERENCES sales_product(organization_id, id)
);
CREATE TABLE sales_contract (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id), number varchar(40) NOT NULL,
    quote_id uuid NOT NULL, quote_version_id uuid NOT NULL, customer_id uuid NOT NULL, opportunity_id uuid NOT NULL,
    name varchar(200) NOT NULL, total numeric(14,2) NOT NULL CHECK (total > 0), currency char(3) NOT NULL DEFAULT 'CNY' CHECK (currency = 'CNY'),
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SIGNED','COMPLETED','CANCELLED')),
    starts_on date NOT NULL, ends_on date NOT NULL CHECK (ends_on >= starts_on),
    terms varchar(5000) NOT NULL, signed_on date, signed_file_id uuid,
    version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
    UNIQUE (organization_id, id), UNIQUE (organization_id, number), UNIQUE (organization_id, quote_id),
    FOREIGN KEY (organization_id, quote_id, quote_version_id) REFERENCES sales_quote_version(organization_id, quote_id, id),
    FOREIGN KEY (organization_id, customer_id, opportunity_id) REFERENCES opportunity(organization_id, customer_id, id),
    FOREIGN KEY (organization_id, signed_file_id) REFERENCES uploaded_file(organization_id, id),
    CHECK ((status IN ('SIGNED','COMPLETED') AND signed_on IS NOT NULL AND signed_file_id IS NOT NULL) OR status IN ('DRAFT','CANCELLED'))
);
CREATE TABLE sales_payment_plan (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL, contract_id uuid NOT NULL,
    position integer NOT NULL CHECK (position > 0), name varchar(120) NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0), due_on date NOT NULL,
    UNIQUE (organization_id, id), UNIQUE (organization_id, contract_id, id), UNIQUE (organization_id, contract_id, position),
    FOREIGN KEY (organization_id, contract_id) REFERENCES sales_contract(organization_id, id)
);
CREATE TABLE sales_receipt (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL, contract_id uuid NOT NULL, plan_id uuid NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0), received_on date NOT NULL,
    reference varchar(200) NOT NULL CHECK (btrim(reference) <> ''), note varchar(1000) NOT NULL,
    request_key uuid NOT NULL, created_by uuid NOT NULL, created_at timestamptz NOT NULL,
    UNIQUE (organization_id, request_key),
    UNIQUE (organization_id, contract_id, reference),
    FOREIGN KEY (organization_id, contract_id, plan_id) REFERENCES sales_payment_plan(organization_id, contract_id, id),
    FOREIGN KEY (organization_id, created_by) REFERENCES organization_member(organization_id, id)
);
CREATE TABLE sales_commercial_event (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization(id),
    entity_type varchar(12) NOT NULL CHECK (entity_type IN ('QUOTE','CONTRACT')), entity_id uuid NOT NULL,
    actor_member_id uuid NOT NULL, summary varchar(1500) NOT NULL, created_at timestamptz NOT NULL,
    FOREIGN KEY (organization_id, actor_member_id) REFERENCES organization_member(organization_id, id)
);
CREATE INDEX ix_sales_quote_list ON sales_quote(organization_id, updated_at DESC);
CREATE INDEX ix_sales_contract_list ON sales_contract(organization_id, updated_at DESC);
CREATE INDEX ix_sales_receipt_plan ON sales_receipt(organization_id, plan_id);
CREATE INDEX ix_sales_payment_due ON sales_payment_plan(organization_id, due_on);
CREATE INDEX ix_sales_commercial_history ON sales_commercial_event(organization_id, entity_type, entity_id, created_at DESC);
