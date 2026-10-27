package com.cai.trading.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.cai.trading.domain.DomainTestFixtures.money;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void should_add_money_in_same_currency() {
        assertEquals(new Money(new BigDecimal("15.00"), "CNY"), money("10").add(money("5")));
    }

    @Test
    void should_subtract_money_in_same_currency() {
        assertEquals(money("5"), money("10").subtract(money("5")));
    }

    @Test
    void should_reject_subtraction_that_becomes_negative() {
        assertThrows(RuntimeException.class, () -> money("5").subtract(money("10")));
    }

    @Test
    void should_reject_addition_across_currencies() {
        Money usd = new Money(new BigDecimal("1"), "USD");
        assertThrows(DomainException.class, () -> money("1").add(usd));
    }

    @Test
    void should_reject_comparison_across_currencies() {
        Money usd = new Money(new BigDecimal("1"), "USD");
        assertThrows(DomainException.class, () -> money("1").compareTo(usd));
    }

    @Test
    void should_treat_different_scales_as_equal_business_values() {
        assertEquals(money("1.0"), money("1.00"));
        assertEquals(money("1.0").hashCode(), money("1.00").hashCode());
    }

    @Test
    void should_reject_negative_money() {
        assertThrows(RuntimeException.class, () -> money("-0.01"));
    }

    @Test
    void should_compare_money_by_numeric_value() {
        assertTrue(money("100").compareTo(money("99.99")) > 0);
    }
}
