package com.cai.trading.application;

import com.cai.trading.domain.*;
import com.cai.trading.engine.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** 单实例 v0.1 的按 Symbol 撮合引擎注册表。 */
public final class InMemoryMatchingEngineRegistry implements MatchingEngineRegistry {
    private final ConcurrentMap<Symbol, MatchingEngine> engines = new ConcurrentHashMap<>();
    private final AtomicLong tradeSequence = new AtomicLong();
    private final Clock clock;

    public InMemoryMatchingEngineRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public MatchingEngine engineFor(Symbol symbol) {
        return engines.computeIfAbsent(symbol,
                key -> new MatchingEngine(new OrderBook(key), tradeFactory()));
    }

    private TradeFactory tradeFactory() {
        return (maker, taker, price, quantity) -> {
            long sequence = tradeSequence.incrementAndGet();
            UUID id = UUID.nameUUIDFromBytes((maker.symbol().value() + ":" + sequence)
                    .getBytes(StandardCharsets.UTF_8));
            AccountId buyer = maker.side() == Side.BUY ? maker.accountId() : taker.accountId();
            AccountId seller = maker.side() == Side.SELL ? maker.accountId() : taker.accountId();
            return new Trade(new TradeId(id), maker.symbol(), maker.id(), taker.id(),
                    buyer, seller, price, quantity, Instant.now(clock));
        };
    }
}

