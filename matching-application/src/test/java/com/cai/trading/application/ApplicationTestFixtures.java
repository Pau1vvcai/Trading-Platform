package com.cai.trading.application;

import com.cai.trading.domain.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ApplicationTestFixtures {
    static final Symbol SYMBOL = new Symbol("AAPL/CNY");
    static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    private ApplicationTestFixtures() {
    }

    static Price price(String value) {
        return new Price(new BigDecimal(value));
    }

    static Quantity quantity(String value) {
        return new Quantity(new BigDecimal(value));
    }

    static Money money(String value) {
        return new Money(new BigDecimal(value), "CNY");
    }

    static AccountId accountId(String name) {
        return new AccountId(uuid("account-" + name));
    }

    static Account account(String name, String cash, String availablePosition) {
        Position position = new Position(SYMBOL, quantity(availablePosition), quantity("0"));
        return new Account(accountId(name), new UserId(uuid("user-" + name)),
                money(cash), money("0"), Map.of(SYMBOL, position), 0L);
    }

    static Order limit(String name, Account account, Side side, String limit, String amount, long sequence) {
        return Order.limit(new OrderId(uuid("order-" + name)), account.id(), SYMBOL, side,
                price(limit), quantity(amount), TIME.plusSeconds(sequence), sequence);
    }

    static Order market(String name, Account account, Side side, String amount, long sequence) {
        return Order.market(new OrderId(uuid("order-" + name)), account.id(), SYMBOL, side,
                quantity(amount), TIME.plusSeconds(sequence), sequence);
    }

    static PlaceOrderCommand limitCommand(Account account, Side side, String limit, String amount) {
        return new PlaceOrderCommand("key-" + side, account.id(), SYMBOL, side, OrderType.LIMIT,
                Optional.of(price(limit)), quantity(amount), Optional.empty());
    }

    static PlaceOrderCommand marketBuyCommand(Account account, String amount, String maxSpend) {
        return new PlaceOrderCommand("key-market-buy", account.id(), SYMBOL, Side.BUY, OrderType.MARKET,
                Optional.empty(), quantity(amount), Optional.of(money(maxSpend)));
    }

    static Trade trade(Order maker, Order taker, String executionPrice, String amount) {
        AccountId buyer = maker.side() == Side.BUY ? maker.accountId() : taker.accountId();
        AccountId seller = maker.side() == Side.SELL ? maker.accountId() : taker.accountId();
        return new Trade(new TradeId(uuid("trade-" + maker.id() + taker.id())), SYMBOL,
                maker.id(), taker.id(), buyer, seller, price(executionPrice), quantity(amount), TIME);
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}

