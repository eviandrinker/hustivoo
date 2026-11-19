-- Invoices (Rechnungen) with payments, tenant-bound references, document snapshots and guards.

CREATE TABLE invoice_number_counters (
    business_id UUID PRIMARY KEY REFERENCES businesses(id),
    last_value BIGINT NOT NULL CHECK (last_value > 0)
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES businesses(id),
    customer_id UUID NOT NULL,
    quote_id UUID,
    invoice_number VARCHAR(32) NOT NULL CHECK (invoice_number ~ '^RE-[0-9]{4,}$'),
    -- Stored lifecycle. OVERDUE is never stored: it is derived from due_date and the open amount.
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    issue_date DATE NOT NULL,
    payment_term_days INTEGER NOT NULL CHECK (payment_term_days BETWEEN 0 AND 365),
    due_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    introduction_text VARCHAR(4000),
    closing_text VARCHAR(4000),
    notes VARCHAR(4000),
    subtotal NUMERIC(19,2) NOT NULL CHECK (subtotal >= 0),
    tax_total NUMERIC(19,2) NOT NULL CHECK (tax_total >= 0),
    grand_total NUMERIC(19,2) NOT NULL CHECK (grand_total >= 0),
    -- Sum of active (not reversed) payments; maintained in the same transaction as the payments.
    paid_total NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (paid_total >= 0),
    -- Customer snapshot (refreshed while drafting, frozen when issued).
    customer_number VARCHAR(32) NOT NULL,
    customer_name VARCHAR(255) NOT NULL CHECK (length(btrim(customer_name)) > 0),
    customer_company_name VARCHAR(255),
    customer_contact_name VARCHAR(255),
    customer_street VARCHAR(255),
    customer_postal_code VARCHAR(20),
    customer_city VARCHAR(120),
    customer_country VARCHAR(2) NOT NULL CHECK (customer_country ~ '^[A-Z]{2}$'),
    customer_email VARCHAR(320),
    -- Sender snapshot (as for quotes, V7): name/country while drafting, everything when issued.
    sender_name VARCHAR(255) NOT NULL CHECK (length(btrim(sender_name)) > 0),
    sender_country VARCHAR(2) NOT NULL CHECK (sender_country ~ '^[A-Z]{2}$'),
    sender_street VARCHAR(255),
    sender_postal_code VARCHAR(20),
    sender_city VARCHAR(120),
    sender_email VARCHAR(320),
    sender_phone VARCHAR(40),
    sender_website VARCHAR(255),
    sender_vat_number VARCHAR(40),
    sender_iban VARCHAR(34),
    sender_bank_name VARCHAR(120),
    sender_document_footer VARCHAR(160),
    sender_brand_color VARCHAR(7) CHECK (sender_brand_color IS NULL OR sender_brand_color ~ '^#[0-9A-F]{6}$'),
    sender_logo_id UUID,
    issued_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoices_customer FOREIGN KEY (business_id, customer_id) REFERENCES customers(business_id, id),
    CONSTRAINT fk_invoices_quote FOREIGN KEY (business_id, quote_id) REFERENCES quotes(business_id, id),
    CONSTRAINT fk_invoices_sender_logo FOREIGN KEY (sender_logo_id, business_id) REFERENCES business_logos(id, business_id),
    CONSTRAINT uq_invoices_number UNIQUE (business_id, invoice_number),
    CONSTRAINT uq_invoices_business_id_id UNIQUE (business_id, id),
    CONSTRAINT ck_invoices_due_date CHECK (due_date = issue_date + payment_term_days),
    CONSTRAINT ck_invoices_totals CHECK (grand_total = subtotal + tax_total),
    CONSTRAINT ck_invoices_paid CHECK (paid_total <= grand_total),
    -- The stored payment status always agrees with the booked payments.
    CONSTRAINT ck_invoices_payment_status CHECK (
        (status = 'DRAFT' AND paid_total = 0)
        OR (status = 'CANCELLED' AND paid_total = 0)
        OR (status = 'ISSUED' AND paid_total = 0)
        OR (status = 'PARTIALLY_PAID' AND paid_total > 0 AND paid_total < grand_total)
        OR (status = 'PAID' AND paid_total = grand_total AND grand_total > 0)),
    CONSTRAINT ck_invoices_issued_at CHECK ((status = 'DRAFT') = (issued_at IS NULL)),
    CONSTRAINT ck_invoices_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)
        AND (cancellation_reason IS NULL OR status = 'CANCELLED'))
);
-- One complete invoice per quote. Partial invoices would replace this index by a sum check.
CREATE UNIQUE INDEX uq_invoices_quote ON invoices(quote_id) WHERE quote_id IS NOT NULL;
CREATE INDEX ix_invoices_business_issue ON invoices(business_id, issue_date DESC, invoice_number DESC);
CREATE INDEX ix_invoices_business_status ON invoices(business_id, status);
CREATE INDEX ix_invoices_business_customer ON invoices(business_id, customer_id);
CREATE INDEX ix_invoices_sender_logo_id ON invoices(sender_logo_id) WHERE sender_logo_id IS NOT NULL;

CREATE TABLE invoice_items (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
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
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (business_id, invoice_id) REFERENCES invoices(business_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_items_service FOREIGN KEY (business_id, service_id) REFERENCES services(business_id, id),
    CONSTRAINT ck_invoice_items_total CHECK (line_total = line_subtotal + line_tax),
    CONSTRAINT uq_invoice_items_position UNIQUE (invoice_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_invoice_items_invoice ON invoice_items(invoice_id, position);
CREATE INDEX ix_invoice_items_service ON invoice_items(business_id, service_id) WHERE service_id IS NOT NULL;

-- Payments are append-only. A wrong booking is reversed (with reason) and booked again.
CREATE TABLE invoice_payments (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    payment_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    method VARCHAR(16) NOT NULL CHECK (method IN ('BANK_TRANSFER', 'QR_BILL', 'CASH', 'CARD', 'TWINT', 'OTHER')),
    reference VARCHAR(140),
    note VARCHAR(1000),
    recorded_by UUID NOT NULL REFERENCES users(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMPTZ,
    reversed_by UUID REFERENCES users(id),
    reversal_reason VARCHAR(500),
    CONSTRAINT fk_invoice_payments_invoice FOREIGN KEY (business_id, invoice_id) REFERENCES invoices(business_id, id),
    CONSTRAINT ck_invoice_payments_reversal CHECK (
        (reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL)
        OR (reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND length(btrim(reversal_reason)) > 0))
);
CREATE INDEX ix_invoice_payments_invoice ON invoice_payments(invoice_id, payment_date, recorded_at);

-- Defence in depth for the documented rules:
--   DRAFT -> DRAFT (edit) | ISSUED; ISSUED <-> PARTIALLY_PAID <-> PAID (payments only);
--   ISSUED -> CANCELLED (without active payments); CANCELLED is final.
-- After issuing, only status, paid_total and the cancellation fields may change.
CREATE FUNCTION invoices_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Only draft invoices can be deleted' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'DRAFT' THEN
        IF NEW.status NOT IN ('DRAFT', 'ISSUED') THEN
            RAISE EXCEPTION 'Invalid invoice status transition' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status = 'CANCELLED'
            OR (NEW.status = 'CANCELLED' AND OLD.status <> 'ISSUED')
            OR NEW.status = 'DRAFT'
            OR (to_jsonb(NEW) - 'status' - 'paid_total' - 'cancelled_at' - 'cancellation_reason' - 'updated_at')
               IS DISTINCT FROM (to_jsonb(OLD) - 'status' - 'paid_total' - 'cancelled_at' - 'cancellation_reason' - 'updated_at') THEN
        RAISE EXCEPTION 'Issued invoices cannot be modified' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_invoices_guard BEFORE UPDATE OR DELETE ON invoices FOR EACH ROW EXECUTE FUNCTION invoices_guard();

CREATE FUNCTION invoice_items_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_status VARCHAR(16);
BEGIN
    SELECT status INTO parent_status FROM invoices
        WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.invoice_id ELSE NEW.invoice_id END;
    IF parent_status IS NOT NULL AND parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Items of issued invoices cannot be modified' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.invoice_id <> OLD.invoice_id THEN
        RAISE EXCEPTION 'Invoice items cannot be moved' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;
CREATE TRIGGER trg_invoice_items_guard BEFORE INSERT OR UPDATE OR DELETE ON invoice_items FOR EACH ROW EXECUTE FUNCTION invoice_items_guard();

-- Payments: only for issued, not cancelled invoices; never deleted; only a single reversal is allowed.
CREATE FUNCTION invoice_payments_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_status VARCHAR(16);
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payments cannot be deleted' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'INSERT' THEN
        SELECT status INTO parent_status FROM invoices WHERE id = NEW.invoice_id;
        IF parent_status NOT IN ('ISSUED', 'PARTIALLY_PAID') OR NEW.reversed_at IS NOT NULL THEN
            RAISE EXCEPTION 'Payments can only be booked on open invoices' USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.reversed_at IS NOT NULL
            OR (to_jsonb(NEW) - 'reversed_at' - 'reversed_by' - 'reversal_reason')
               IS DISTINCT FROM (to_jsonb(OLD) - 'reversed_at' - 'reversed_by' - 'reversal_reason') THEN
        RAISE EXCEPTION 'Payments are immutable; reverse and book again' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_invoice_payments_guard BEFORE INSERT OR UPDATE OR DELETE ON invoice_payments FOR EACH ROW EXECUTE FUNCTION invoice_payments_guard();

-- paid_total must equal the sum of active payments; checked at commit, after all statements ran.
CREATE FUNCTION invoice_paid_total_check() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    target UUID;
    stored NUMERIC(19,2);
    booked NUMERIC(19,2);
BEGIN
    -- Separate branches: PL/pgSQL resolves every NEW field referenced in an expression.
    IF TG_TABLE_NAME = 'invoices' THEN
        target := NEW.id;
    ELSE
        target := NEW.invoice_id;
    END IF;
    SELECT paid_total INTO stored FROM invoices WHERE id = target;
    SELECT coalesce(sum(amount), 0) INTO booked FROM invoice_payments WHERE invoice_id = target AND reversed_at IS NULL;
    IF stored IS NOT NULL AND stored <> booked THEN
        RAISE EXCEPTION 'Invoice paid total does not match its payments' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_invoices_paid_total AFTER INSERT OR UPDATE ON invoices
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION invoice_paid_total_check();
CREATE CONSTRAINT TRIGGER trg_invoice_payments_paid_total AFTER INSERT OR UPDATE ON invoice_payments
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION invoice_paid_total_check();
