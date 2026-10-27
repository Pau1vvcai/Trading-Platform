package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.util.List;

/** 每隔固定根数投入当前可用现金的一部分。 */
public final class DollarCostAveragingStrategy implements TradingStrategy {
    private final int interval;
    private final BigDecimal allocation;

    public DollarCostAveragingStrategy(int interval, BigDecimal allocation) {
        if (interval <= 0) throw new IllegalArgumentException("interval must be positive");
        if (allocation.signum() <= 0 || allocation.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("allocation must be within (0, 1]");
        }
        this.interval = interval;
        this.allocation = allocation;
    }

    @Override public String name() { return "DCA(" + interval + ")"; }
    @Override public StrategyDecision decide(int index, List<MarketBar> bars) {
        return index % interval == 0 ? StrategyDecision.buy(allocation) : StrategyDecision.hold();
    }
}
