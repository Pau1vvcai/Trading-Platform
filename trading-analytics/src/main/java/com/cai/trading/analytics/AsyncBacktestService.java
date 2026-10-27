package com.cai.trading.analytics;

import com.cai.trading.domain.Symbol;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 有界职责的异步回测服务；任务状态每次变化都会先持久化再通知。 */
public final class AsyncBacktestService implements AutoCloseable {
    private final BacktestEngine engine;
    private final BacktestJobRepository jobs;
    private final ExecutorService executor;
    private final Clock clock;
    private final BacktestJobListener listener;

    public AsyncBacktestService(BacktestEngine engine, BacktestJobRepository jobs,
                                int workers, Clock clock, BacktestJobListener listener) {
        this.engine = Objects.requireNonNull(engine);
        this.jobs = Objects.requireNonNull(jobs);
        if (workers <= 0) throw new IllegalArgumentException("workers must be positive");
        this.executor = Executors.newFixedThreadPool(workers);
        this.clock = Objects.requireNonNull(clock);
        this.listener = Objects.requireNonNull(listener);
    }

    public UUID submit(Symbol symbol, List<MarketBar> bars, TradingStrategy strategy,
                       BigDecimal initialCapital, BigDecimal feeRate) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now(clock);
        update(new BacktestJob(id, symbol, strategy.name(), BacktestJobStatus.QUEUED,
                0, now, now, null, null));
        executor.submit(() -> execute(id, symbol, bars, strategy, initialCapital, feeRate, now));
        return id;
    }

    public BacktestJob get(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("Backtest job not found"));
    }

    private void execute(UUID id, Symbol symbol, List<MarketBar> bars, TradingStrategy strategy,
                         BigDecimal initialCapital, BigDecimal feeRate, Instant createdAt) {
        update(new BacktestJob(id, symbol, strategy.name(), BacktestJobStatus.RUNNING,
                10, createdAt, Instant.now(clock), null, null));
        try {
            BacktestResult result = engine.run(bars, strategy, initialCapital, feeRate);
            update(new BacktestJob(id, symbol, strategy.name(), BacktestJobStatus.SUCCEEDED,
                    100, createdAt, Instant.now(clock), result, null));
        } catch (RuntimeException error) {
            update(new BacktestJob(id, symbol, strategy.name(), BacktestJobStatus.FAILED,
                    100, createdAt, Instant.now(clock), null, safeMessage(error)));
        }
    }

    private void update(BacktestJob job) {
        jobs.save(job);
        listener.onChanged(job);
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Backtest failed" : message;
    }

    @Override public void close() { executor.shutdown(); }
}
