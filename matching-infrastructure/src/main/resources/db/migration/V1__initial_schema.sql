CREATE TABLE users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    currency VARCHAR(12) NOT NULL,
    available_cash NUMERIC(38, 8) NOT NULL CHECK (available_cash >= 0),
    frozen_cash NUMERIC(38, 8) NOT NULL CHECK (frozen_cash >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE positions (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    available_quantity NUMERIC(38, 8) NOT NULL CHECK (available_quantity >= 0),
    frozen_quantity NUMERIC(38, 8) NOT NULL CHECK (frozen_quantity >= 0),
    PRIMARY KEY (account_id, symbol)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type VARCHAR(8) NOT NULL CHECK (order_type IN ('LIMIT', 'MARKET')),
    limit_price NUMERIC(38, 8),
    original_quantity NUMERIC(38, 8) NOT NULL CHECK (original_quantity > 0),
    filled_quantity NUMERIC(38, 8) NOT NULL CHECK (
        filled_quantity >= 0 AND filled_quantity <= original_quantity
    ),
    status VARCHAR(24) NOT NULL CHECK (
        status IN ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELED', 'REJECTED')
    ),
    accepted_at TIMESTAMPTZ NOT NULL,
    acceptance_sequence BIGINT NOT NULL CHECK (acceptance_sequence > 0),
    spending_limit NUMERIC(38, 8),
    reserved_cash NUMERIC(38, 8),
    cash_currency VARCHAR(12),
    CONSTRAINT order_price_by_type CHECK (
        (order_type = 'LIMIT' AND limit_price IS NOT NULL AND limit_price > 0)
        OR (order_type = 'MARKET' AND limit_price IS NULL)
    ),
    CONSTRAINT non_negative_order_cash CHECK (
        (spending_limit IS NULL OR spending_limit >= 0)
        AND (reserved_cash IS NULL OR reserved_cash >= 0)
    ),
    UNIQUE (symbol, acceptance_sequence)
);

CREATE INDEX idx_orders_active_symbol_sequence
    ON orders(symbol, acceptance_sequence)
    WHERE status IN ('NEW', 'PARTIALLY_FILLED');

CREATE TABLE trades (
    id UUID PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    maker_order_id UUID NOT NULL REFERENCES orders(id),
    taker_order_id UUID NOT NULL REFERENCES orders(id),
    buyer_account_id UUID NOT NULL REFERENCES accounts(id),
    seller_account_id UUID NOT NULL REFERENCES accounts(id),
    price NUMERIC(38, 8) NOT NULL CHECK (price > 0),
    quantity NUMERIC(38, 8) NOT NULL CHECK (quantity > 0),
    executed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_trades_symbol_time ON trades(symbol, executed_at);

CREATE TABLE idempotency_records (
    account_id UUID NOT NULL REFERENCES accounts(id),
    idempotency_key VARCHAR(200) NOT NULL,
    request_digest VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'SUCCEEDED', 'REJECTED')),
    result_type VARCHAR(255),
    result_payload TEXT,
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    PRIMARY KEY (account_id, idempotency_key)
);
