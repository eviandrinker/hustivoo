-- Quotes (Angebote) with tenant-bound references, document snapshots and status guards.

CREATE TABLE quote_number_counters (
    business_id UUID PRIMARY KEY REFERENCES businesses(id),
    last_value BIGINT NOT NULL CHECK (last_value > 0)
);

CREATE TABLE quotes (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    customer_id UUID NOT NULL,
    quote_number VARCHAR(32) NOT NULL CHECK (quote_number ~ '^ANG-[0-9]{4,}$'),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED')),
    issue_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    introduction_text VARCHAR(4000),
    closing_text VARCHAR(4000),
    notes VARCHAR(4000),
    subtotal NUMERIC(19,2) NOT NULL CHECK (subtotal >= 0),
    tax_total NUMERIC(19,2) NOT NULL CHECK (tax_total >= 0),
    grand_total NUMERIC(19,2) NOT NULL CHECK (grand_total >= 0),
    -- Document snapshots: later changes to business or customer master data do not alter issued quotes.
    sender_name VARCHAR(255) NOT NULL CHECK (length(btrim(sender_name)) > 0),
    customer_number VARCHAR(32) NOT NULL,
    customer_name VARCHAR(255) NOT NULL CHECK (length(btrim(customer_name)) > 0),
    customer_company_name VARCHAR(255),
    customer_contact_name VARCHAR(255),
    customer_street VARCHAR(255),
    customer_postal_code VARCHAR(20),
    customer_city VARCHAR(120),
    customer_country VARCHAR(2) NOT NULL CHECK (customer_country ~ '^[A-Z]{2}$'),
    customer_email VARCHAR(320),
    sent_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quotes_customer FOREIGN KEY (business_id, customer_id) REFERENCES customers(business_id, id),
    CONSTRAINT uq_quotes_number UNIQUE (business_id, quote_number),
    CONSTRAINT uq_quotes_business_id_id UNIQUE (business_id, id),
    CONSTRAINT ck_quotes_validity CHECK (valid_until >= issue_date),
    CONSTRAINT ck_quotes_totals CHECK (grand_total = subtotal + tax_total),
    CONSTRAINT ck_quotes_sent_at CHECK ((status = 'DRAFT') = (sent_at IS NULL)),
    CONSTRAINT ck_quotes_decided_at CHECK ((status IN ('ACCEPTED', 'REJECTED')) = (decided_at IS NOT NULL))
);
CREATE INDEX ix_quotes_business_issue ON quotes(business_id, issue_date DESC, quote_number DESC);
CREATE INDEX ix_quotes_business_status ON quotes(business_id, status);
CREATE INDEX ix_quotes_business_customer ON quotes(business_id, customer_id);

CREATE TABLE quote_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    quote_id UUID NOT NULL,
    service_id UUID,
    position INTEGER NOT NULL CHECK (position >= 1),
    description VARCHAR(4000) NOT NULL CHECK (length(btrim(description)) > 0),
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(16) NOT NULL CHECK (unit IN ('HOUR', 'DAY', 'FIXED', 'PIECE', 'MONTH')),
    unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(5,2) NOT NULL CHECK (tax_rate >= 0 AND tax_rate <= 100),
    line_subtotal NUMERIC(19,2) NOT NULL CHECK (line_subtotal >= 0),
    line_tax NUMERIC(19,2) NOT NULL CHECK (line_tax >= 0),
    line_total NUMERIC(19,2) NOT NULL CHECK (line_total >= 0),
    CONSTRAINT fk_quote_items_quote FOREIGN KEY (business_id, quote_id) REFERENCES quotes(business_id, id) ON DELETE CASCADE,
    -- Composite reference: a quote can only point at services of its own business. NULL = free position.
    CONSTRAINT fk_quote_items_service FOREIGN KEY (business_id, service_id) REFERENCES services(business_id, id),
    CONSTRAINT ck_quote_items_total CHECK (line_total = line_subtotal + line_tax),
    CONSTRAINT uq_quote_items_position UNIQUE (quote_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_quote_items_quote ON quote_items(quote_id, position);
CREATE INDEX ix_quote_items_service ON quote_items(business_id, service_id) WHERE service_id IS NOT NULL;

-- Defence in depth for the documented status rules:
--   DRAFT -> DRAFT (edit) | SENT;  SENT -> ACCEPTED | REJECTED;  ACCEPTED/REJECTED are final.
-- Issued quotes keep their content; only drafts can be deleted.
CREATE FUNCTION quotes_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Only draft quotes can be deleted' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'DRAFT' THEN
        IF NEW.status NOT IN ('DRAFT', 'SENT') THEN
            RAISE EXCEPTION 'Invalid quote status transition' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status <> 'SENT' OR NEW.status NOT IN ('ACCEPTED', 'REJECTED')
            OR (to_jsonb(NEW) - 'status' - 'decided_at' - 'updated_at')
               IS DISTINCT FROM (to_jsonb(OLD) - 'status' - 'decided_at' - 'updated_at') THEN
        RAISE EXCEPTION 'Issued quotes cannot be modified' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_quotes_guard BEFORE UPDATE OR DELETE ON quotes FOR EACH ROW EXECUTE FUNCTION quotes_guard();

CREATE FUNCTION quote_items_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_status VARCHAR(16);
BEGIN
    SELECT status INTO parent_status FROM quotes
        WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.quote_id ELSE NEW.quote_id END;
    -- A missing parent only occurs during the cascade of an (already guarded) draft deletion.
    IF parent_status IS NOT NULL AND parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Items of issued quotes cannot be modified' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.quote_id <> OLD.quote_id THEN
        RAISE EXCEPTION 'Quote items cannot be moved' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;
CREATE TRIGGER trg_quote_items_guard BEFORE INSERT OR UPDATE OR DELETE ON quote_items FOR EACH ROW EXECUTE FUNCTION quote_items_guard();
