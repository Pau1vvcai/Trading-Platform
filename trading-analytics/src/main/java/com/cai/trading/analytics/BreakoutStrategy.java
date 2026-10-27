package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.util.List;

/** 收盘价突破此前窗口最高价时买入，跌破最低价时卖出。 */
public final class BreakoutStrategy implements TradingStrategy {
    private final int lookback;

    public BreakoutStrategy(int lookback) {
        if (lookback <= 0) throw new IllegalArgumentException("lookback must be positive");
        this.lookback = lookback;
    }

    @Override public String name() { return "BREAKOUT(" + lookback + ")"; }

    @Override
    public StrategyDecision decide(int index, List<MarketBar> bars) {
        if (index < lookback) return StrategyDecision.hold();
        BigDecimal highest = bars.get(index - lookback).high();
        BigDecimal lowest = bars.get(index - lookback).low();
        for (int i = index - lookback + 1; i < index; i++) {
            highest = highest.max(bars.get(i).high());
            lowest = lowest.min(bars.get(i).low());
        }
        BigDecimal close = bars.get(index).close();
        if (close.compareTo(highest) > 0) return StrategyDecision.buyAll();
        if (close.compareTo(lowest) < 0) return StrategyDecision.sell();
        return StrategyDecision.hold();
    }
}
