package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.util.Objects;

/** allocation 表示本次最多使用可用现金的比例。 */
public record StrategyDecision(StrategyAction action, BigDecimal allocation) {
    public StrategyDecision {
        Objects.requireNonNull(action);
        Objects.requireNonNull(allocation);
        if (allocation.signum() < 0 || allocation.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("allocation must be within [0, 1]");
        }
        if (action != StrategyAction.BUY && allocation.signum() != 0) {
            throw new IllegalArgumentException("only BUY may allocate cash");
        }
    }

    public static StrategyDecision buyAll() { return new StrategyDecision(StrategyAction.BUY, BigDecimal.ONE); }
    public static StrategyDecision buy(BigDecimal allocation) { return new StrategyDecision(StrategyAction.BUY, allocation); }
    public static StrategyDecision sell() { return new StrategyDecision(StrategyAction.SELL, BigDecimal.ZERO); }
    public static StrategyDecision hold() { return new StrategyDecision(StrategyAction.HOLD, BigDecimal.ZERO); }
}
