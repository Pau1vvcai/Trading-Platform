package com.cai.trading.engine;

import com.cai.trading.domain.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class EngineTestFixtures {
    static final Symbol SYMBOL = new Symbol("AAPL/CNY");
    static final Symbol OTHER_SYMBOL = new Symbol("MSFT/CNY");
    static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private EngineTestFixtures() {
    }

    static Price price(String value) {
        return new Price(new BigDecimal(value));
    }

    static Quantity quantity(String value) {
        return new Quantity(new BigDecimal(value));
    }

    static AccountId account(String name) {
        return new AccountId(uuid("account-" + name));
    }

    static Order limit(Side side, String price, String quantity, long sequence) {
        return limit("order-" + side + "-" + sequence, account(side + "-" + sequence),
                SYMBOL, side, price, quantity, sequence);
    }

    static Order limit(String name, AccountId accountId, Symbol symbol, Side side,
                       String price, String quantity, long sequence) {
        return Order.limit(new OrderId(uuid(name)), accountId, symbol, side,
                price(price), quantity(quantity), BASE_TIME.plusMillis(sequence), sequence);
    }

    static Order market(Side side, String quantity, long sequence) {
        return Order.market(new OrderId(uuid("market-" + side + "-" + sequence)),
                account("market-" + side + "-" + sequence), SYMBOL, side,
                quantity(quantity), BASE_TIME.plusMillis(sequence), sequence);
    }

    static TradeFactory deterministicTradeFactory() {
        AtomicLong sequence = new AtomicLong();
        return (maker, taker, executionPrice, executionQuantity) -> {
            long tradeSequence = sequence.incrementAndGet();
            AccountId buyer = maker.side() == Side.BUY ? maker.accountId() : taker.accountId();
            AccountId seller = maker.side() == Side.SELL ? maker.accountId() : taker.accountId();
            return new Trade(
                    new TradeId(uuid("trade-" + tradeSequence)),
                    maker.symbol(),
                    maker.id(),
                    taker.id(),
                    buyer,
                    seller,
                    executionPrice,
                    executionQuantity,
                    BASE_TIME.plusSeconds(tradeSequence));
        };
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}

