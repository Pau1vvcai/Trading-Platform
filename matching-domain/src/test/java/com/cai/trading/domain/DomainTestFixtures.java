package com.cai.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class DomainTestFixtures {
    static final Symbol SYMBOL = new Symbol("AAPL/CNY");
    static final Instant ACCEPTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private DomainTestFixtures() {
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

    static Order limit(Side side, String price, String quantity, long sequence) {
        return Order.limit(
                new OrderId(UUID.nameUUIDFromBytes((side + "-" + sequence).getBytes())),
                new AccountId(UUID.nameUUIDFromBytes(("account-" + side + "-" + sequence).getBytes())),
                SYMBOL,
                side,
                price(price),
                quantity(quantity),
                ACCEPTED_AT.plusSeconds(sequence),
                sequence);
    }

    static Account account(String cash, Position... positions) {
        Map<Symbol, Position> bySymbol = positions.length == 0
                ? Map.of()
                : Map.of(positions[0].symbol(), positions[0]);
        return new Account(
                new AccountId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                money(cash),
                money("0"),
                bySymbol,
                0L);
    }
}

