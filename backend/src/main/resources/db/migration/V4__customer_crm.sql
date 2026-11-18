ALTER TABLE customers ADD COLUMN customer_number VARCHAR(32),
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('LEAD', 'ACTIVE', 'INACTIVE'));
WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY business_id ORDER BY created_at, id) AS n FROM customers
)
UPDATE customers c SET customer_number = 'K-' || lpad(n.n::text, greatest(4, length(n.n::text)), '0')
FROM numbered n WHERE c.id = n.id;
ALTER TABLE customers ALTER COLUMN customer_number SET NOT NULL;
ALTER TABLE customers ADD CONSTRAINT uq_customer_number UNIQUE (business_id, customer_number);
CREATE TABLE customer_number_counters (
    business_id UUID PRIMARY KEY REFERENCES businesses(id),
    last_value BIGINT NOT NULL CHECK (last_value > 0)
);
INSERT INTO customer_number_counters (business_id, last_value) SELECT business_id, count(*) FROM customers GROUP BY business_id;

CREATE TABLE customer_contacts (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    first_name VARCHAR(120) NOT NULL CHECK (length(btrim(first_name)) > 0),
    last_name VARCHAR(120) NOT NULL CHECK (length(btrim(last_name)) > 0),
    job_title VARCHAR(120),
    email VARCHAR(320),
    phone VARCHAR(50),
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (business_id, customer_id) REFERENCES customers(business_id, id)
);
CREATE INDEX ix_customer_contacts_scope ON customer_contacts(business_id, customer_id);
CREATE UNIQUE INDEX uq_customer_primary_contact ON customer_contacts(business_id, customer_id) WHERE primary_contact;

CREATE TABLE customer_notes (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    content VARCHAR(4000) NOT NULL CHECK (length(btrim(content)) > 0),
    created_by UUID REFERENCES users(id),
    author_name VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (business_id, customer_id) REFERENCES customers(business_id, id)
);
CREATE INDEX ix_customer_notes_scope ON customer_notes(business_id, customer_id, created_at);
-- Old notes have no recorded author: preserve them without inventing an identity.
INSERT INTO customer_notes(id, business_id, customer_id, content, author_name, created_at)
SELECT id, business_id, id, notes, 'Importiert', created_at FROM customers WHERE notes IS NOT NULL AND length(btrim(notes)) > 0;

CREATE TABLE customer_activities (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    event VARCHAR(40) NOT NULL CHECK (event IN ('CREATED','UPDATED','STATUS_CHANGED','CONTACT_CREATED','CONTACT_UPDATED','CONTACT_REMOVED','NOTE_ADDED','ARCHIVED','RESTORED')),
    created_by UUID REFERENCES users(id),
    author_name VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (business_id, customer_id) REFERENCES customers(business_id, id)
);
CREATE INDEX ix_customer_activities_scope ON customer_activities(business_id, customer_id, created_at);
INSERT INTO customer_activities(id, business_id, customer_id, event, author_name, created_at)
SELECT id, business_id, id, 'CREATED', 'Importiert', created_at FROM customers;
