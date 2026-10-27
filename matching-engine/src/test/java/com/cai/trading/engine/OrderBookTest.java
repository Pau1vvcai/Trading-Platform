package com.cai.trading.engine;

import com.cai.trading.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cai.trading.engine.EngineTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {
    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook(SYMBOL);
    }

    @Test
    void should_return_empty_best_prices_for_new_book() {
        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.bestAsk().isEmpty());
    }

    @Test
    void should_choose_highest_buy_price_as_best_bid() {
        Order low = limit(Side.BUY, "9", "1", 1);
        Order high = limit(Side.BUY, "11", "1", 2);
        book.add(low);
        book.add(high);

        assertSame(high, book.bestBid().orElseThrow());
    }

    @Test
    void should_choose_lowest_sell_price_as_best_ask() {
        Order high = limit(Side.SELL, "11", "1", 1);
        Order low = limit(Side.SELL, "9", "1", 2);
        book.add(high);
        book.add(low);

        assertSame(low, book.bestAsk().orElseThrow());
    }

    @Test
    void should_keep_fifo_order_within_same_price_level() {
        Order first = limit(Side.BUY, "10", "1", 1);
        Order second = limit(Side.BUY, "10.00", "1", 2);
        book.add(first);
        book.add(second);

        assertEquals(List.of(first, second), book.ordersAt(price("10"), Side.BUY));
        assertSame(first, book.bestBid().orElseThrow());
    }

    @Test
    void should_reject_duplicate_order_id() {
        Order order = limit(Side.BUY, "10", "1", 1);
        book.add(order);

        DomainException error = assertThrows(DomainException.class, () -> book.add(order));
        assertEquals(ErrorCode.DUPLICATE_ORDER, error.code());
    }

    @Test
    void should_reject_order_for_another_symbol() {
        Order wrongSymbol = limit("wrong-symbol", account("buyer"), OTHER_SYMBOL,
                Side.BUY, "10", "1", 1);

        DomainException error = assertThrows(DomainException.class, () -> book.add(wrongSymbol));
        assertEquals(ErrorCode.INVALID_ORDER, error.code());
    }

    @Test
    void should_reject_market_order_because_it_must_never_rest() {
        DomainException error = assertThrows(DomainException.class,
                () -> book.add(market(Side.BUY, "1", 1)));
        assertEquals(ErrorCode.INVALID_ORDER, error.code());
    }

    @Test
    void should_remove_order_by_id_and_preserve_other_fifo_entries() {
        Order first = limit(Side.SELL, "10", "1", 1);
        Order middle = limit(Side.SELL, "10", "1", 2);
        Order last = limit(Side.SELL, "10", "1", 3);
        book.add(first);
        book.add(middle);
        book.add(last);

        assertSame(middle, book.remove(middle.id()).orElseThrow());
        assertEquals(List.of(first, last), book.ordersAt(price("10"), Side.SELL));
    }

    @Test
    void should_remove_empty_price_level_after_last_order_is_removed() {
        Order best = limit(Side.BUY, "11", "1", 1);
        Order next = limit(Side.BUY, "10", "1", 2);
        book.add(best);
        book.add(next);

        book.remove(best.id());

        assertTrue(book.ordersAt(price("11"), Side.BUY).isEmpty());
        assertSame(next, book.bestBid().orElseThrow());
    }

    @Test
    void should_return_empty_when_removing_unknown_order() {
        assertTrue(book.remove(new OrderId(java.util.UUID.randomUUID())).isEmpty());
    }

    @Test
    void should_return_immutable_level_and_snapshot_lists() {
        Order bid = limit(Side.BUY, "9", "1", 1);
        Order ask = limit(Side.SELL, "11", "1", 2);
        book.add(bid);
        book.add(ask);

        List<Order> level = book.ordersAt(price("9"), Side.BUY);
        OrderBookSnapshot snapshot = book.snapshot();

        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> level.add(bid)),
                () -> assertThrows(UnsupportedOperationException.class, () -> snapshot.bids().add(bid)),
                () -> assertThrows(UnsupportedOperationException.class, () -> snapshot.asks().clear()));
    }
}

