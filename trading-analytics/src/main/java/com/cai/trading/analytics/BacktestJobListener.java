package com.cai.trading.analytics;

@FunctionalInterface
public interface BacktestJobListener {
    void onChanged(BacktestJob job);
    static BacktestJobListener noop() { return job -> { }; }
}
