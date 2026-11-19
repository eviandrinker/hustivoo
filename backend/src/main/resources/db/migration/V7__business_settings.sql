-- Business settings: master data, payment details and document branding.
ALTER TABLE businesses
    ADD COLUMN street VARCHAR(255),
    ADD COLUMN postal_code VARCHAR(20),
    ADD COLUMN city VARCHAR(120),
    ADD COLUMN email VARCHAR(320),
    ADD COLUMN phone VARCHAR(40),
    ADD COLUMN website VARCHAR(255),
    ADD COLUMN vat_number VARCHAR(40),
    ADD COLUMN iban VARCHAR(34),
    ADD COLUMN bank_name VARCHAR(120),
    ADD COLUMN document_footer VARCHAR(160),
    ADD COLUMN brand_color VARCHAR(7),
    ADD COLUMN logo_id UUID,
    ADD CONSTRAINT ck_businesses_settings_text CHECK (
        (street IS NULL OR length(btrim(street)) > 0) AND (postal_code IS NULL OR length(btrim(postal_code)) > 0)
        AND (city IS NULL OR length(btrim(city)) > 0) AND (email IS NULL OR length(btrim(email)) > 0)
        AND (phone IS NULL OR length(btrim(phone)) > 0) AND (website IS NULL OR length(btrim(website)) > 0)
        AND (vat_number IS NULL OR length(btrim(vat_number)) > 0) AND (bank_name IS NULL OR length(btrim(bank_name)) > 0)
        AND (document_footer IS NULL OR length(btrim(document_footer)) > 0)),
    ADD CONSTRAINT ck_businesses_iban CHECK (iban IS NULL OR iban ~ '^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$'),
    ADD CONSTRAINT ck_businesses_brand_color CHECK (brand_color IS NULL OR brand_color ~ '^#[0-9A-F]{6}$');

-- Logos are immutable, normalised PNGs owned by exactly one business. Replacing a logo inserts a new
-- row; rows still referenced by issued quotes are kept so those documents never change.
CREATE TABLE business_logos (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    content BYTEA NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    size_bytes INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_logos_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    CONSTRAINT uq_business_logos_tenant UNIQUE (id, business_id),
    CONSTRAINT ck_business_logos_type CHECK (content_type = 'image/png'),
    CONSTRAINT ck_business_logos_dimensions CHECK (width BETWEEN 1 AND 4000 AND height BETWEEN 1 AND 4000),
    CONSTRAINT ck_business_logos_size CHECK (size_bytes = octet_length(content) AND size_bytes BETWEEN 1 AND 1048576)
);
CREATE INDEX ix_business_logos_business_id ON business_logos (business_id);

CREATE FUNCTION business_logos_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Business logos are immutable' USING ERRCODE = 'integrity_constraint_violation';
END;
$$;
CREATE TRIGGER trg_business_logos_guard BEFORE UPDATE ON business_logos FOR EACH ROW EXECUTE FUNCTION business_logos_guard();

-- The composite key guarantees that a business can only point at its own logo.
ALTER TABLE businesses ADD CONSTRAINT fk_businesses_logo FOREIGN KEY (logo_id, id) REFERENCES business_logos (id, business_id);

-- Sender snapshot of issued quotes. Drafts print the current settings; sending freezes them here.
ALTER TABLE quotes
    ADD COLUMN sender_country VARCHAR(2),
    ADD COLUMN sender_street VARCHAR(255),
    ADD COLUMN sender_postal_code VARCHAR(20),
    ADD COLUMN sender_city VARCHAR(120),
    ADD COLUMN sender_email VARCHAR(320),
    ADD COLUMN sender_phone VARCHAR(40),
    ADD COLUMN sender_website VARCHAR(255),
    ADD COLUMN sender_vat_number VARCHAR(40),
    ADD COLUMN sender_iban VARCHAR(34),
    ADD COLUMN sender_bank_name VARCHAR(120),
    ADD COLUMN sender_document_footer VARCHAR(160),
    ADD COLUMN sender_brand_color VARCHAR(7),
    ADD COLUMN sender_logo_id UUID,
    ADD CONSTRAINT fk_quotes_sender_logo FOREIGN KEY (sender_logo_id, business_id) REFERENCES business_logos (id, business_id),
    ADD CONSTRAINT ck_quotes_sender_brand_color CHECK (sender_brand_color IS NULL OR sender_brand_color ~ '^#[0-9A-F]{6}$');
CREATE INDEX ix_quotes_sender_logo_id ON quotes (sender_logo_id) WHERE sender_logo_id IS NOT NULL;

-- Existing quotes printed the business country as "home" country; freeze it once so later country
-- changes cannot alter issued documents. The issued-quote guard is bypassed for this one-time backfill only.
ALTER TABLE quotes DISABLE TRIGGER trg_quotes_guard;
UPDATE quotes q SET sender_country = b.country FROM businesses b WHERE b.id = q.business_id;
ALTER TABLE quotes ENABLE TRIGGER trg_quotes_guard;
ALTER TABLE quotes
    ALTER COLUMN sender_country SET NOT NULL,
    ADD CONSTRAINT ck_quotes_sender_country CHECK (sender_country ~ '^[A-Z]{2}$');
