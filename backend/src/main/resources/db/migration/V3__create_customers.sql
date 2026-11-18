CREATE TABLE customers (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('PRIVATE', 'COMPANY')),
    display_name VARCHAR(255) NOT NULL,
    company_name VARCHAR(255),
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    email VARCHAR(320),
    phone VARCHAR(50),
    street VARCHAR(255),
    postal_code VARCHAR(20),
    city VARCHAR(120),
    country VARCHAR(2) NOT NULL DEFAULT 'CH',
    notes VARCHAR(4000),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customers_display_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_customers_country CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT uq_customers_business_id_id UNIQUE (business_id, id)
);
CREATE INDEX ix_customers_business_archived_name ON customers (business_id, archived, display_name);
