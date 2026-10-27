CREATE TABLE ml_research_results (
    experiment_id UUID PRIMARY KEY,
    dataset_version VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    prediction_target VARCHAR(128) NOT NULL,
    model_name VARCHAR(64) NOT NULL,
    sample_count INTEGER NOT NULL CHECK (sample_count > 0),
    fold_count INTEGER NOT NULL CHECK (fold_count > 0),
    baseline_mae NUMERIC(30, 12) NOT NULL CHECK (baseline_mae >= 0),
    model_mae NUMERIC(30, 12) NOT NULL CHECK (model_mae >= 0),
    improvement_pct NUMERIC(20, 8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    leakage_policy TEXT NOT NULL,
    cost_assumption TEXT NOT NULL,
    fold_metrics JSONB NOT NULL
);

CREATE INDEX idx_ml_research_results_symbol_created
    ON ml_research_results(symbol, created_at DESC);
