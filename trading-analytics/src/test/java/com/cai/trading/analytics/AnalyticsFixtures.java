package com.cai.trading.analytics;

import com.cai.trading.domain.Quantity;
import com.cai.trading.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

final class AnalyticsFixtures {
    static final Symbol SYMBOL = new Symbol("BTC/USD");
    static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    static MarketBar bar(int minute, String price) {
        BigDecimal value = new BigDecimal(price);
        return new MarketBar(SYMBOL, START.plus(minute, ChronoUnit.MINUTES), value,
                value.add(BigDecimal.ONE), value.subtract(BigDecimal.ONE), value,
                new Quantity(BigDecimal.TEN));
    }

    static List<MarketBar> bars(String... prices) {
        return IntStream.range(0, prices.length).mapToObj(i -> bar(i, prices[i])).toList();
    }

    private AnalyticsFixtures() { }
}
