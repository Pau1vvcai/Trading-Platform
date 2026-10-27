package com.cai.trading.engine;

import com.cai.trading.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cai.trading.engine.EngineTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(new OrderBook(SYMBOL), deterministicTradeFactory());
    }

    @Test
    void should_rest_limit_order_when_opposite_book_is_empty() {
        Order buy = limit(Side.BUY, "10", "2", 1);

        MatchResult result = engine.submit(buy);

        assertTrue(result.trades().isEmpty());
        assertSame(buy, engine.snapshot().bids().getFirst());
        assertEquals(OrderStatus.NEW, buy.status());
    }

    @Test
    void should_rest_both_orders_when_prices_do_not_cross() {
        Order sell = limit(Side.SELL, "11", "1", 1);
        Order buy = limit(Side.BUY, "10", "1", 2);

        engine.submit(sell);
        MatchResult result = engine.submit(buy);

        assertTrue(result.trades().isEmpty());
        assertEquals(List.of(buy), engine.snapshot().bids());
        assertEquals(List.of(sell), engine.snapshot().asks());
    }

    @Test
    void should_fully_match_two_orders_at_maker_price() {
        Order makerSell = limit(Side.SELL, "9", "2", 1);
        Order takerBuy = limit(Side.BUY, "10", "2", 2);
        engine.submit(makerSell);

        MatchResult result = engine.submit(takerBuy);

        Trade trade = result.trades().getFirst();
        assertEquals(price("9"), trade.executionPrice());
        assertEquals(quantity("2"), trade.executionQuantity());
        assertEquals(makerSell.id(), trade.makerOrderId());
        assertEquals(takerBuy.id(), trade.takerOrderId());
        assertEquals(OrderStatus.FILLED, makerSell.status());
        assertEquals(OrderStatus.FILLED, takerBuy.status());
        assertTrue(engine.snapshot().asks().isEmpty());
        assertTrue(engine.snapshot().bids().isEmpty());
    }

    @Test
    void should_leave_maker_at_front_when_maker_is_partially_filled() {
        Order maker = limit(Side.SELL, "10", "5", 1);
        Order taker = limit(Side.BUY, "10", "2", 2);
        engine.submit(maker);

        engine.submit(taker);

        assertEquals(OrderStatus.PARTIALLY_FILLED, maker.status());
        assertEquals(quantity("3"), maker.remainingQuantity());
        assertSame(maker, engine.snapshot().asks().getFirst());
        assertEquals(OrderStatus.FILLED, taker.status());
    }

    @Test
    void should_rest_remaining_limit_taker_after_partial_fill() {
        Order maker = limit(Side.SELL, "10", "2", 1);
        Order taker = limit(Side.BUY, "10", "5", 2);
        engine.submit(maker);

        engine.submit(taker);

        assertEquals(OrderStatus.FILLED, maker.status());
        assertEquals(OrderStatus.PARTIALLY_FILLED, taker.status());
        assertEquals(quantity("3"), taker.remainingQuantity());
        assertSame(taker, engine.snapshot().bids().getFirst());
    }

    @Test
    void should_match_across_multiple_price_levels_in_price_order() {
        Order sellAtNine = limit(Side.SELL, "9", "1", 1);
        Order sellAtTen = limit(Side.SELL, "10", "2", 2);
        Order sellAtEleven = limit(Side.SELL, "11", "3", 3);
        engine.submit(sellAtEleven);
        engine.submit(sellAtNine);
        engine.submit(sellAtTen);

        MatchResult result = engine.submit(limit(Side.BUY, "11", "4", 4));

        assertEquals(List.of(price("9"), price("10"), price("11")),
                result.trades().stream().map(Trade::executionPrice).toList());
        assertEquals(List.of(quantity("1"), quantity("2"), quantity("1")),
                result.trades().stream().map(Trade::executionQuantity).toList());
        assertEquals(quantity("2"), sellAtEleven.remainingQuantity());
    }

    @Test
    void should_match_earlier_order_first_when_prices_are_equal() {
        Order first = limit(Side.SELL, "10", "1", 1);
        Order second = limit(Side.SELL, "10", "1", 2);
        engine.submit(first);
        engine.submit(second);

        MatchResult result = engine.submit(limit(Side.BUY, "10", "1", 3));

        assertEquals(first.id(), result.trades().getFirst().makerOrderId());
        assertEquals(OrderStatus.FILLED, first.status());
        assertEquals(OrderStatus.NEW, second.status());
    }

    @Test
    void should_cancel_unfilled_market_remainder_and_never_rest_it() {
        Order maker = limit(Side.SELL, "10", "1", 1);
        Order marketBuy = market(Side.BUY, "3", 2);
        engine.submit(maker);

        MatchResult result = engine.submit(marketBuy);

        assertEquals(1, result.trades().size());
        assertEquals(quantity("1"), marketBuy.filledQuantity());
        assertEquals(quantity("2"), marketBuy.remainingQuantity());
        assertEquals(OrderStatus.CANCELED, marketBuy.status());
        assertTrue(engine.snapshot().bids().isEmpty());
    }

    @Test
    void should_cancel_market_order_with_zero_fill_when_book_is_empty() {
        Order marketSell = market(Side.SELL, "1", 1);

        MatchResult result = engine.submit(marketSell);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.CANCELED, marketSell.status());
        assertTrue(engine.snapshot().asks().isEmpty());
    }

    @Test
    void should_cancel_resting_order_and_remove_it_from_book() {
        Order order = limit(Side.BUY, "10", "2", 1);
        engine.submit(order);

        Order canceled = engine.cancel(order.id());

        assertSame(order, canceled);
        assertEquals(OrderStatus.CANCELED, canceled.status());
        assertTrue(engine.snapshot().bids().isEmpty());
    }

    @Test
    void should_reject_cancel_for_unknown_or_terminal_order() {
        DomainException missing = assertThrows(DomainException.class,
                () -> engine.cancel(new OrderId(java.util.UUID.randomUUID())));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, missing.code());

        Order maker = limit(Side.SELL, "10", "1", 1);
        engine.submit(maker);
        engine.submit(limit(Side.BUY, "10", "1", 2));
        assertThrows(DomainException.class, () -> engine.cancel(maker.id()));
    }

    @Test
    void should_reject_duplicate_or_terminal_order_submission() {
        Order order = limit(Side.BUY, "10", "1", 1);
        engine.submit(order);
        assertThrows(DomainException.class, () -> engine.submit(order));

        Order filled = limit(Side.SELL, "10", "1", 2);
        engine.submit(filled);
        engine.submit(limit(Side.BUY, "10", "1", 3));
        assertThrows(DomainException.class, () -> engine.submit(filled));
    }

    @Test
    void should_cancel_taker_remainder_when_next_maker_belongs_to_same_account() {
        AccountId shared = account("self-trader");
        Order ownMaker = limit("own-maker", shared, SYMBOL, Side.SELL, "10", "1", 1);
        Order ownTaker = limit("own-taker", shared, SYMBOL, Side.BUY, "10", "1", 2);
        engine.submit(ownMaker);

        MatchResult result = engine.submit(ownTaker);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.CANCELED, ownTaker.status());
        assertEquals(OrderStatus.NEW, ownMaker.status());
    }

    @Test
    void should_return_immutable_trade_and_update_lists() {
        MatchResult result = engine.submit(limit(Side.BUY, "10", "1", 1));
        assertAll(
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> result.trades().add(null)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> result.orderUpdates().clear()));
    }
}
