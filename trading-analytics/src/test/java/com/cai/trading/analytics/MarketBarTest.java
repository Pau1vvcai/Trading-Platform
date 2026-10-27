package com.cai.trading.analytics;

import com.cai.trading.domain.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.cai.trading.analytics.AnalyticsFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketBarTest {
    @Test void should_create_valid_bar() { assertEquals(0, bar(0, "100").close().compareTo(new BigDecimal("100"))); }

    @Test void should_reject_non_positive_price() {
        assertThrows(IllegalArgumentException.class, () -> new MarketBar(SYMBOL, START,
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, Quantity.zero()));
    }

    @Test void should_reject_high_below_close() {
        assertThrows(IllegalArgumentException.class, () -> new MarketBar(SYMBOL, START,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(11), Quantity.zero()));
    }

    @Test void should_reject_zero_volume() {
        assertThrows(IllegalArgumentException.class, () -> new MarketBar(SYMBOL, START,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, Quantity.zero()));
    }
}
