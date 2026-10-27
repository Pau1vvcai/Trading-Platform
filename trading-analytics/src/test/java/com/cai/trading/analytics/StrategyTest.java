package com.cai.trading.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.cai.trading.analytics.AnalyticsFixtures.bars;
import static org.junit.jupiter.api.Assertions.*;

class StrategyTest {
    @Test
    void should_buy_on_sma_upward_cross() {
        var values = bars("10", "9", "8", "9", "12");
        assertEquals(StrategyAction.BUY, new SmaCrossStrategy(2, 3).decide(4, values).action());
    }

    @Test
    void should_sell_on_sma_downward_cross() {
        var values = bars("8", "10", "12", "10", "7");
        assertEquals(StrategyAction.SELL, new SmaCrossStrategy(2, 3).decide(4, values).action());
    }

    @Test
    void should_buy_on_breakout() {
        var values = bars("10", "11", "12", "14");
        assertEquals(StrategyAction.BUY, new BreakoutStrategy(3).decide(3, values).action());
    }

    @Test
    void should_sell_on_breakdown() {
        var values = bars("14", "13", "12", "10");
        assertEquals(StrategyAction.SELL, new BreakoutStrategy(3).decide(3, values).action());
    }

    @Test
    void should_invest_on_dca_interval() {
        DollarCostAveragingStrategy strategy = new DollarCostAveragingStrategy(2, new BigDecimal("0.25"));
        assertAll(
                () -> assertEquals(StrategyAction.BUY, strategy.decide(0, bars("10")).action()),
                () -> assertEquals(StrategyAction.HOLD, strategy.decide(1, bars("10", "11")).action()),
                () -> assertEquals(new BigDecimal("0.25"), strategy.decide(2, bars("10", "11", "12")).allocation()));
    }
}
