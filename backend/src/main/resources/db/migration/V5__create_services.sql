CREATE TABLE services (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    name VARCHAR(255) NOT NULL CHECK (length(trim(name)) > 0),
    description VARCHAR(4000),
    category VARCHAR(120),
    unit VARCHAR(16) NOT NULL CHECK (unit IN ('HOUR', 'DAY', 'FIXED', 'PIECE', 'MONTH')),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'CHF' CHECK (currency ~ '^[A-Z]{3}$'),
    tax_rate NUMERIC(5,2) CHECK (tax_rate >= 0 AND tax_rate <= 100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_id, id)
);
CREATE INDEX idx_services_business_active_name ON services(business_id, active, name, id);
CREATE INDEX idx_services_business_active_price ON services(business_id, active, unit_price, id);
CREATE INDEX idx_services_business_category ON services(business_id, lower(category));
