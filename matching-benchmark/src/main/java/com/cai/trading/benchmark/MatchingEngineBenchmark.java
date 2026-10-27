package com.cai.trading.benchmark;

import com.cai.trading.engine.MatchingEngine;
import com.cai.trading.engine.*;
import com.cai.trading.domain.*;
import org.openjdk.jmh.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/**
 * JMH 基准入口。
 * 分别准备“无成交 / 高成交率 / 深订单簿”三类可重复数据，热路径不生成随机数或打日志。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(2)
@State(Scope.Thread)
public class MatchingEngineBenchmark {
    private MatchingEngine engine;
    private final AtomicLong sequence = new AtomicLong();
    private Symbol symbol;

    @Param({"NO_MATCH", "HIGH_MATCH", "DEEP_BOOK"})
    private String workload;

    @Param({"1000"})
    private int depth;

    @Setup(Level.Iteration)
    public void setUp() {
        sequence.set(0);
        symbol = new Symbol("AAPL/CNY");
        TradeFactory trades = (maker, taker, price, quantity) -> new Trade(
                new TradeId(UUID.randomUUID()), symbol, maker.id(), taker.id(),
                taker.side() == Side.BUY ? taker.accountId() : maker.accountId(),
                taker.side() == Side.SELL ? taker.accountId() : maker.accountId(),
                price, quantity, Instant.EPOCH);
        engine = new MatchingEngine(new OrderBook(symbol), trades);

        if (workload.equals("HIGH_MATCH")) {
            engine.submit(limit(Side.SELL, "100", "100000000", next()));
        } else if (workload.equals("DEEP_BOOK")) {
            for (int i = 0; i < depth; i++) {
                engine.submit(limit(Side.SELL, Integer.toString(100 + i), "1", next()));
            }
        }
    }

    @Benchmark
    public Object submitOrder() {
        long next = next();
        return switch (workload) {
            case "NO_MATCH" -> engine.submit(limit(Side.BUY, "90", "1", next));
            case "HIGH_MATCH" -> engine.submit(marketBuy("1", next));
            case "DEEP_BOOK" -> engine.submit(limit(Side.BUY, "100", "1", next));
            default -> throw new IllegalStateException("Unknown workload: " + workload);
        };
    }

    private Order limit(Side side, String price, String quantity, long sequence) {
        return Order.limit(new OrderId(UUID.randomUUID()), new AccountId(UUID.randomUUID()), symbol,
                side, new Price(new BigDecimal(price)), new Quantity(new BigDecimal(quantity)),
                Instant.EPOCH.plusNanos(sequence), sequence);
    }

    private Order marketBuy(String quantity, long sequence) {
        return Order.market(new OrderId(UUID.randomUUID()), new AccountId(UUID.randomUUID()), symbol,
                Side.BUY, new Quantity(new BigDecimal(quantity)),
                Instant.EPOCH.plusNanos(sequence), sequence);
    }

    private long next() {
        return sequence.incrementAndGet();
    }
}

