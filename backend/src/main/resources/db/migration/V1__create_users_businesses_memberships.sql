CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email CHECK (email <> '' AND email = lower(btrim(email))),
    CONSTRAINT ck_users_password_hash CHECK (length(btrim(password_hash)) > 0)
);

CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CHF',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_businesses_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_businesses_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    business_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_memberships_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    CONSTRAINT uq_memberships_user_business UNIQUE (user_id, business_id),
    CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'MEMBER'))
);

-- The unique user/business index already supports lookups by user_id.
CREATE INDEX ix_memberships_business_id ON memberships (business_id);
