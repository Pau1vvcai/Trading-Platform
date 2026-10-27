package com.cai.trading.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.cai.trading.domain.DomainTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    @Test
    void should_create_limit_order_with_new_status_and_full_remaining_quantity() {
        Order order = limit(Side.BUY, "10", "5", 1);

        assertEquals(OrderType.LIMIT, order.type());
        assertEquals(OrderStatus.NEW, order.status());
        assertEquals(price("10"), order.limitPrice().orElseThrow());
        assertEquals(quantity("0"), order.filledQuantity());
        assertEquals(quantity("5"), order.remainingQuantity());
        assertTrue(order.isActive());
    }

    @Test
    void should_create_market_order_without_limit_price() {
        Order order = Order.market(
                new OrderId(UUID.randomUUID()),
                new AccountId(UUID.randomUUID()),
                SYMBOL,
                Side.BUY,
                quantity("2"),
                ACCEPTED_AT,
                1L);

        assertEquals(OrderType.MARKET, order.type());
        assertTrue(order.limitPrice().isEmpty());
    }

    @Test
    void should_reject_limit_order_without_price() {
        assertThrows(RuntimeException.class, () -> new Order(
                new OrderId(UUID.randomUUID()), new AccountId(UUID.randomUUID()), SYMBOL,
                Side.BUY, OrderType.LIMIT, null, quantity("1"), ACCEPTED_AT, 1L));
    }

    @Test
    void should_reject_market_order_with_limit_price() {
        assertThrows(RuntimeException.class, () -> new Order(
                new OrderId(UUID.randomUUID()), new AccountId(UUID.randomUUID()), SYMBOL,
                Side.BUY, OrderType.MARKET, price("10"), quantity("1"), ACCEPTED_AT, 1L));
    }

    @Test
    void should_reject_non_positive_acceptance_sequence() {
        assertThrows(RuntimeException.class, () -> Order.limit(
                new OrderId(UUID.randomUUID()), new AccountId(UUID.randomUUID()), SYMBOL,
                Side.BUY, price("10"), quantity("1"), ACCEPTED_AT, 0L));
    }

    @Test
    void should_be_partially_filled_when_execution_is_smaller_than_remaining() {
        Order order = limit(Side.BUY, "10", "5", 1);

        order.fill(quantity("2"));

        assertEquals(OrderStatus.PARTIALLY_FILLED, order.status());
        assertEquals(quantity("2"), order.filledQuantity());
        assertEquals(quantity("3"), order.remainingQuantity());
        assertTrue(order.isActive());
    }

    @Test
    void should_be_filled_when_execution_consumes_remaining_quantity() {
        Order order = limit(Side.BUY, "10", "5", 1);

        order.fill(quantity("5"));

        assertEquals(OrderStatus.FILLED, order.status());
        assertEquals(quantity("0"), order.remainingQuantity());
        assertFalse(order.isActive());
    }

    @Test
    void should_accumulate_multiple_partial_fills() {
        Order order = limit(Side.BUY, "10", "5", 1);

        order.fill(quantity("2"));
        order.fill(quantity("3"));

        assertEquals(quantity("5"), order.filledQuantity());
        assertEquals(OrderStatus.FILLED, order.status());
    }

    @Test
    void should_reject_fill_larger_than_remaining_quantity() {
        Order order = limit(Side.BUY, "10", "5", 1);
        assertThrows(RuntimeException.class, () -> order.fill(quantity("6")));
    }

    @Test
    void should_reject_zero_fill() {
        Order order = limit(Side.BUY, "10", "5", 1);
        assertThrows(RuntimeException.class, () -> order.fill(quantity("0")));
    }

    @Test
    void should_cancel_new_or_partially_filled_order_but_keep_fill_history() {
        Order order = limit(Side.SELL, "10", "5", 1);
        order.fill(quantity("2"));

        order.cancel();

        assertEquals(OrderStatus.CANCELED, order.status());
        assertEquals(quantity("2"), order.filledQuantity());
        assertEquals(quantity("3"), order.remainingQuantity());
        assertFalse(order.isActive());
    }

    @Test
    void should_reject_fill_or_cancel_after_terminal_state() {
        Order filled = limit(Side.BUY, "10", "1", 1);
        filled.fill(quantity("1"));
        assertAll(
                () -> assertThrows(RuntimeException.class, () -> filled.fill(quantity("1"))),
                () -> assertThrows(RuntimeException.class, filled::cancel));

        Order canceled = limit(Side.SELL, "10", "1", 2);
        canceled.cancel();
        assertAll(
                () -> assertThrows(RuntimeException.class, () -> canceled.fill(quantity("1"))),
                () -> assertThrows(RuntimeException.class, canceled::cancel));
    }

    @Test
    void should_define_entity_equality_by_order_id_only() {
        OrderId sharedId = new OrderId(UUID.randomUUID());
        Order first = Order.limit(sharedId, new AccountId(UUID.randomUUID()), SYMBOL,
                Side.BUY, price("10"), quantity("1"), ACCEPTED_AT, 1L);
        Order sameIdentity = Order.limit(sharedId, new AccountId(UUID.randomUUID()), SYMBOL,
                Side.SELL, price("11"), quantity("2"), ACCEPTED_AT.plusSeconds(1), 2L);

        assertEquals(first, sameIdentity);
        assertEquals(first.hashCode(), sameIdentity.hashCode());
    }
}

