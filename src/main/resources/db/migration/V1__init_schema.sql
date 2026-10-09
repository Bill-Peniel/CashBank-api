CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    phone_number  VARCHAR(20)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone_number)
);

CREATE TABLE wallets (
    id            UUID PRIMARY KEY,
    wallet_number VARCHAR(12)    NOT NULL,
    user_id       UUID           NOT NULL REFERENCES users (id),
    balance       NUMERIC(19, 2) NOT NULL DEFAULT 0,
    currency      VARCHAR(3)     NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_wallets_number UNIQUE (wallet_number),
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_wallets_balance_positive CHECK (balance >= 0)
);

CREATE TABLE transactions (
    id                    UUID PRIMARY KEY,
    reference             VARCHAR(30)    NOT NULL,
    type                  VARCHAR(20)    NOT NULL,
    amount                NUMERIC(19, 2) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    source_wallet_id      UUID REFERENCES wallets (id),
    destination_wallet_id UUID REFERENCES wallets (id),
    description           VARCHAR(255),
    initiated_by          UUID           NOT NULL REFERENCES users (id),
    idempotency_key       VARCHAR(100),
    created_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_transactions_reference UNIQUE (reference),
    CONSTRAINT uk_transactions_idempotency UNIQUE (initiated_by, idempotency_key),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_wallets CHECK (source_wallet_id IS NOT NULL OR destination_wallet_id IS NOT NULL)
);

CREATE INDEX idx_transactions_source_created ON transactions (source_wallet_id, created_at DESC);
CREATE INDEX idx_transactions_destination_created ON transactions (destination_wallet_id, created_at DESC);

CREATE TABLE beneficiaries (
    id         UUID PRIMARY KEY,
    owner_id   UUID         NOT NULL REFERENCES users (id),
    wallet_id  UUID         NOT NULL REFERENCES wallets (id),
    alias      VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_beneficiaries_owner_wallet UNIQUE (owner_id, wallet_id)
);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(150) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    reference  VARCHAR(30),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);

CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY,
    actor_id      UUID,
    actor_email   VARCHAR(255),
    action        VARCHAR(40)  NOT NULL,
    outcome       VARCHAR(10)  NOT NULL,
    resource_type VARCHAR(40),
    resource_id   VARCHAR(100),
    details       VARCHAR(1000),
    ip_address    VARCHAR(45),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
