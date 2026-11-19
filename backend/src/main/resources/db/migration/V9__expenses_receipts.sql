CREATE TABLE suppliers (
 id UUID PRIMARY KEY, business_id UUID NOT NULL REFERENCES businesses(id),
 name VARCHAR(255) NOT NULL CHECK (length(btrim(name))>0), contact_person VARCHAR(255), email VARCHAR(320), phone VARCHAR(50),
 street VARCHAR(255), house_number VARCHAR(30), postal_code VARCHAR(20), city VARCHAR(120), country VARCHAR(2) NOT NULL DEFAULT 'CH',
 vat_number VARCHAR(50), iban VARCHAR(34), notes VARCHAR(4000), archived BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(business_id,id)
);
CREATE INDEX ix_suppliers_business_name ON suppliers(business_id,lower(name));
CREATE TABLE expense_categories (
 id UUID PRIMARY KEY, business_id UUID NOT NULL REFERENCES businesses(id), name VARCHAR(120) NOT NULL CHECK(length(btrim(name))>0),
 archived BOOLEAN NOT NULL DEFAULT FALSE, UNIQUE(business_id,id)
);
CREATE UNIQUE INDEX uq_expense_categories_name ON expense_categories(business_id,lower(name));
CREATE TABLE expenses (
 id UUID PRIMARY KEY, business_id UUID NOT NULL REFERENCES businesses(id), supplier_id UUID NOT NULL, category_id UUID NOT NULL,
 supplier_snapshot TEXT NOT NULL, category_name VARCHAR(120) NOT NULL,
 description VARCHAR(1000) NOT NULL CHECK(length(btrim(description))>0), reference VARCHAR(140),
 expense_date DATE NOT NULL, due_date DATE, currency VARCHAR(3) NOT NULL DEFAULT 'CHF' CHECK(currency ~ '^[A-Z]{3}$'),
 net_amount NUMERIC(19,2) NOT NULL CHECK(net_amount>=0 AND net_amount<=99999999999999999.99),
 tax_rate NUMERIC(5,2) CHECK(tax_rate BETWEEN 0 AND 100), tax_amount NUMERIC(19,2) NOT NULL CHECK(tax_amount>=0),
 gross_amount NUMERIC(19,2) NOT NULL CHECK(gross_amount=net_amount+tax_amount AND gross_amount<=99999999999999999.99),
 input_tax VARCHAR(24) NOT NULL DEFAULT 'UNCHECKED' CHECK(input_tax IN ('UNCHECKED','DEDUCTIBLE','NOT_DEDUCTIBLE','PARTIAL')),
 status VARCHAR(16) NOT NULL DEFAULT 'UNPAID' CHECK(status IN ('UNPAID','PAID','CANCELLED')),
 notes VARCHAR(4000), cancellation_reason VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(business_id,id), FOREIGN KEY(business_id,supplier_id) REFERENCES suppliers(business_id,id),
 FOREIGN KEY(business_id,category_id) REFERENCES expense_categories(business_id,id),
 CHECK(tax_rate IS NOT NULL OR tax_amount=0), CHECK(due_date IS NULL OR due_date>=expense_date)
);
CREATE INDEX ix_expenses_business_date ON expenses(business_id,expense_date DESC);
CREATE INDEX ix_expenses_business_status ON expenses(business_id,status);
CREATE TABLE expense_payments (
 id UUID PRIMARY KEY, business_id UUID NOT NULL, expense_id UUID NOT NULL,
 payment_date DATE NOT NULL, method VARCHAR(16) NOT NULL CHECK(method IN ('BANK_TRANSFER','QR_BILL','CASH','CARD','TWINT','OTHER')),
 amount NUMERIC(19,2) NOT NULL CHECK(amount>=0), currency VARCHAR(3) NOT NULL,
 recorded_by UUID NOT NULL REFERENCES users(id), recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 reversed_at TIMESTAMPTZ, reversed_by UUID REFERENCES users(id), reversal_reason VARCHAR(500),
 FOREIGN KEY(business_id,expense_id) REFERENCES expenses(business_id,id),
 CHECK((reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL) OR
 (reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND length(btrim(reversal_reason))>0))
);
CREATE UNIQUE INDEX uq_expense_active_payment ON expense_payments(expense_id) WHERE reversed_at IS NULL;
CREATE TABLE expense_receipts (
 id UUID PRIMARY KEY, business_id UUID NOT NULL, expense_id UUID NOT NULL,
 filename VARCHAR(180) NOT NULL, content_type VARCHAR(30) NOT NULL CHECK(content_type IN ('application/pdf','image/png','image/jpeg')),
 size_bytes BIGINT NOT NULL CHECK(size_bytes>0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(business_id,expense_id) REFERENCES expenses(business_id,id)
);
CREATE INDEX ix_expense_receipts_expense ON expense_receipts(business_id,expense_id);
CREATE FUNCTION expense_payment_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payment history cannot be deleted'; END IF;
 IF OLD.reversed_at IS NOT NULL OR NEW.reversed_at IS NULL OR
 (to_jsonb(NEW)-'reversed_at'-'reversed_by'-'reversal_reason') IS DISTINCT FROM
 (to_jsonb(OLD)-'reversed_at'-'reversed_by'-'reversal_reason') THEN RAISE EXCEPTION 'Reverse payments instead of overwriting'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_expense_payment_guard BEFORE UPDATE OR DELETE ON expense_payments FOR EACH ROW EXECUTE FUNCTION expense_payment_guard();
CREATE FUNCTION expense_payment_status_check() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target UUID; stored VARCHAR; active_count INTEGER;
BEGIN
 IF TG_TABLE_NAME='expenses' THEN target:=NEW.id; ELSE target:=NEW.expense_id; END IF;
 SELECT status INTO stored FROM expenses WHERE id=target;
 SELECT count(*) INTO active_count FROM expense_payments WHERE expense_id=target AND reversed_at IS NULL;
 IF (stored='PAID') IS DISTINCT FROM (active_count=1) THEN RAISE EXCEPTION 'Expense and payment disagree'; END IF;
 RETURN NULL;
END; $$;
CREATE CONSTRAINT TRIGGER trg_expense_status AFTER INSERT OR UPDATE ON expenses DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION expense_payment_status_check();
CREATE CONSTRAINT TRIGGER trg_expense_payment_status AFTER INSERT OR UPDATE ON expense_payments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION expense_payment_status_check();
CREATE FUNCTION expenses_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN
   IF OLD.status<>'UNPAID' OR EXISTS(SELECT 1 FROM expense_payments WHERE expense_id=OLD.id) THEN RAISE EXCEPTION 'Expense history cannot be deleted'; END IF;
   RETURN OLD;
 END IF;
 IF OLD.status='CANCELLED' THEN RAISE EXCEPTION 'Cancelled expense is final'; END IF;
 IF EXISTS(SELECT 1 FROM expense_payments WHERE expense_id=OLD.id) AND
 (to_jsonb(NEW)-'status'-'updated_at'-'cancellation_reason') IS DISTINCT FROM
 (to_jsonb(OLD)-'status'-'updated_at'-'cancellation_reason') THEN RAISE EXCEPTION 'Paid expense details are immutable'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_expenses_guard BEFORE UPDATE OR DELETE ON expenses FOR EACH ROW EXECUTE FUNCTION expenses_guard();
