package com.cai.trading.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.cai.trading.analytics.AnalyticsFixtures.bars;
import static org.junit.jupiter.api.Assertions.*;

class BacktestEngineTest {
    private final BacktestEngine engine = new BacktestEngine();

    @Test
    void should_calculate_positive_return_and_fee() {
        TradingStrategy strategy = scripted(StrategyDecision.buyAll(), StrategyDecision.hold(), StrategyDecision.sell());
        BacktestResult result = engine.run(bars("10", "11", "12"), strategy,
                new BigDecimal("100"), new BigDecimal("0.001"));
        assertAll(
                () -> assertTrue(result.finalEquity().compareTo(new BigDecimal("100")) > 0),
                () -> assertTrue(result.totalReturn().signum() > 0),
                () -> assertTrue(result.totalFees().signum() > 0),
                () -> assertEquals(new BigDecimal("1.00000000"), result.winRate()),
                () -> assertEquals(2, result.trades().size()));
    }

    @Test
    void should_calculate_maximum_drawdown() {
        TradingStrategy buyAndHold = scripted(StrategyDecision.buyAll(), StrategyDecision.hold(), StrategyDecision.hold());
        BacktestResult result = engine.run(bars("10", "12", "6"), buyAndHold,
                new BigDecimal("100"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("0.50000000"), result.maxDrawdown());
    }

    @Test
    void should_keep_result_lists_immutable() {
        BacktestResult result = engine.run(bars("10"), scripted(StrategyDecision.hold()),
                BigDecimal.TEN, BigDecimal.ZERO);
        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> result.equityCurve().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.trades().clear()));
    }

    @Test
    void should_reject_mixed_symbols() {
        MarketBar other = new MarketBar(new com.cai.trading.domain.Symbol("ETH/USD"),
                bars("10").getFirst().timestamp(), BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN,
                new com.cai.trading.domain.Quantity(BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> engine.run(
                List.of(bars("10").getFirst(), other), scripted(StrategyDecision.hold(), StrategyDecision.hold()),
                BigDecimal.TEN, BigDecimal.ZERO));
    }

    private static TradingStrategy scripted(StrategyDecision... decisions) {
        return new TradingStrategy() {
            @Override public String name() { return "SCRIPTED"; }
            @Override public StrategyDecision decide(int index, List<MarketBar> bars) { return decisions[index]; }
        };
    }
}
