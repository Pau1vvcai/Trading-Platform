package com.cai.trading.domain;

import org.junit.jupiter.api.Test;

import static com.cai.trading.domain.DomainTestFixtures.quantity;
import static org.junit.jupiter.api.Assertions.*;

class QuantityTest {
    @Test
    void should_create_numeric_zero() {
        assertTrue(Quantity.zero().isZero());
    }

    @Test
    void should_add_quantities() {
        assertEquals(quantity("5.00"), quantity("2").add(quantity("3.0")));
    }

    @Test
    void should_subtract_quantities() {
        assertEquals(quantity("2"), quantity("5").subtract(quantity("3")));
    }

    @Test
    void should_reject_subtraction_that_becomes_negative() {
        assertThrows(RuntimeException.class, () -> quantity("2").subtract(quantity("3")));
    }

    @Test
    void should_return_smaller_quantity() {
        assertEquals(quantity("2"), quantity("2").min(quantity("3")));
    }

    @Test
    void should_treat_different_scales_as_equal_business_values() {
        assertEquals(quantity("1.0"), quantity("1.00"));
        assertEquals(quantity("1.0").hashCode(), quantity("1.00").hashCode());
    }

    @Test
    void should_reject_negative_quantity() {
        assertThrows(RuntimeException.class, () -> quantity("-0.001"));
    }

    @Test
    void should_compare_by_numeric_value() {
        assertTrue(quantity("10").compareTo(quantity("2")) > 0);
    }
}

