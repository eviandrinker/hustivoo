ALTER TABLE businesses
    ADD COLUMN industry VARCHAR(120),
    ADD COLUMN legal_form VARCHAR(120),
    ADD COLUMN country VARCHAR(2) NOT NULL DEFAULT 'CH',
    ADD CONSTRAINT ck_businesses_industry CHECK (industry IS NULL OR length(btrim(industry)) > 0),
    ADD CONSTRAINT ck_businesses_legal_form CHECK (legal_form IS NULL OR length(btrim(legal_form)) > 0),
    ADD CONSTRAINT ck_businesses_country CHECK (country ~ '^[A-Z]{2}$');
