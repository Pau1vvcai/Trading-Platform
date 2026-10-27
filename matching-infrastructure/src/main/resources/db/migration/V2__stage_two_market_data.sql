CREATE TABLE market_bars (
    symbol VARCHAR(32) NOT NULL,
    bar_time TIMESTAMPTZ NOT NULL,
    open_price NUMERIC(30, 10) NOT NULL CHECK (open_price > 0),
    high_price NUMERIC(30, 10) NOT NULL CHECK (high_price > 0),
    low_price NUMERIC(30, 10) NOT NULL CHECK (low_price > 0),
    close_price NUMERIC(30, 10) NOT NULL CHECK (close_price > 0),
    volume NUMERIC(30, 10) NOT NULL CHECK (volume > 0),
    PRIMARY KEY (symbol, bar_time),
    CHECK (high_price >= open_price AND high_price >= close_price),
    CHECK (low_price <= open_price AND low_price <= close_price)
);

CREATE INDEX idx_market_bars_time ON market_bars(bar_time);
