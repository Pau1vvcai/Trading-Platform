package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** 短均线上穿长均线买入，下穿卖出。 */
public final class SmaCrossStrategy implements TradingStrategy {
    private final int shortWindow;
    private final int longWindow;

    public SmaCrossStrategy(int shortWindow, int longWindow) {
        if (shortWindow <= 0 || longWindow <= shortWindow) {
            throw new IllegalArgumentException("Require 0 < shortWindow < longWindow");
        }
        this.shortWindow = shortWindow;
        this.longWindow = longWindow;
    }

    @Override public String name() { return "SMA(" + shortWindow + "," + longWindow + ")"; }

    @Override
    public StrategyDecision decide(int index, List<MarketBar> bars) {
        if (index < longWindow) return StrategyDecision.hold();
        BigDecimal previousShort = average(bars, index - 1, shortWindow);
        BigDecimal previousLong = average(bars, index - 1, longWindow);
        BigDecimal currentShort = average(bars, index, shortWindow);
        BigDecimal currentLong = average(bars, index, longWindow);
        if (previousShort.compareTo(previousLong) <= 0 && currentShort.compareTo(currentLong) > 0) {
            return StrategyDecision.buyAll();
        }
        if (previousShort.compareTo(previousLong) >= 0 && currentShort.compareTo(currentLong) < 0) {
            return StrategyDecision.sell();
        }
        return StrategyDecision.hold();
    }

    private static BigDecimal average(List<MarketBar> bars, int end, int window) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = end - window + 1; index <= end; index++) sum = sum.add(bars.get(index).close());
        return sum.divide(BigDecimal.valueOf(window), MathContext.DECIMAL64);
    }
}
