package com.cai.trading.domain;

import org.junit.jupiter.api.Test;

import static com.cai.trading.domain.DomainTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PositionTest {
    @Test
    void should_move_available_quantity_to_frozen_when_reserving() {
        Position position = new Position(SYMBOL, quantity("10"), quantity("0"));

        position.freeze(quantity("4"));

        assertEquals(quantity("6"), position.available());
        assertEquals(quantity("4"), position.frozen());
    }

    @Test
    void should_reject_freeze_larger_than_available_position() {
        Position position = new Position(SYMBOL, quantity("1"), quantity("0"));
        assertThrows(DomainException.class, () -> position.freeze(quantity("2")));
    }

    @Test
    void should_release_frozen_quantity_back_to_available() {
        Position position = new Position(SYMBOL, quantity("6"), quantity("4"));

        position.release(quantity("3"));

        assertEquals(quantity("9"), position.available());
        assertEquals(quantity("1"), position.frozen());
    }

    @Test
    void should_consume_only_frozen_quantity_after_sale() {
        Position position = new Position(SYMBOL, quantity("6"), quantity("4"));

        position.debitFrozen(quantity("3"));

        assertEquals(quantity("6"), position.available());
        assertEquals(quantity("1"), position.frozen());
    }

    @Test
    void should_credit_newly_acquired_position() {
        Position position = new Position(SYMBOL, quantity("1"), quantity("0"));
        position.credit(quantity("2"));
        assertEquals(quantity("3"), position.available());
    }

    @Test
    void should_reject_release_or_debit_larger_than_frozen_quantity() {
        Position position = new Position(SYMBOL, quantity("1"), quantity("1"));
        assertAll(
                () -> assertThrows(RuntimeException.class, () -> position.release(quantity("2"))),
                () -> assertThrows(RuntimeException.class, () -> position.debitFrozen(quantity("2"))));
    }
}

