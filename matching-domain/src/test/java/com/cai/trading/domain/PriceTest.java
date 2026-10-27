package com.cai.trading.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.cai.trading.domain.DomainTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PriceTest {
    @Test
    void should_compare_prices_by_numeric_value() {
        assertTrue(price("10.01").compareTo(price("10.00")) > 0);
        assertTrue(price("9.99").compareTo(price("10.00")) < 0);
    }

    @Test
    void should_treat_different_scales_as_equal_business_values() {
        assertEquals(price("1.0"), price("1.00"));
        assertEquals(price("1.0").hashCode(), price("1.00").hashCode());
    }

    @Test
    void should_reject_zero_price() {
        assertThrows(RuntimeException.class, () -> price("0"));
    }

    @Test
    void should_reject_negative_price() {
        assertThrows(RuntimeException.class, () -> price("-0.01"));
    }

    @Test
    void should_reject_null_price() {
        assertThrows(RuntimeException.class, () -> new Price(null));
    }

    @Test
    void should_multiply_price_and_quantity_into_money() {
        Money notional = price("12.50").multiply(quantity("4"), "CNY");

        assertEquals("CNY", notional.currency());
        assertEquals(0, new BigDecimal("50.00").compareTo(notional.amount()));
    }
}

