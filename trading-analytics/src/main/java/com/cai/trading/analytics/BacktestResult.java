package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.util.List;

public record BacktestResult(String strategy, BigDecimal initialCapital, BigDecimal finalEquity,
                             BigDecimal totalReturn, BigDecimal maxDrawdown, BigDecimal winRate,
                             BigDecimal totalFees, List<EquityPoint> equityCurve,
                             List<BacktestTrade> trades) {
    public BacktestResult {
        equityCurve = List.copyOf(equityCurve);
        trades = List.copyOf(trades);
    }
}
