package com.cai.trading.analytics;

import com.cai.trading.domain.Symbol;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 异步任务快照；更新通过 with... 方法创建新对象，方便并发读取。 */
public record BacktestJob(UUID id, Symbol symbol, String strategy, BacktestJobStatus status,
                          int progress, Instant createdAt, Instant updatedAt,
                          BacktestResult result, String error) {
    public BacktestJob {
        Objects.requireNonNull(id); Objects.requireNonNull(symbol); Objects.requireNonNull(strategy);
        Objects.requireNonNull(status); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (progress < 0 || progress > 100) throw new IllegalArgumentException("progress must be 0..100");
    }
    public Optional<BacktestResult> resultOptional() { return Optional.ofNullable(result); }
    public Optional<String> errorOptional() { return Optional.ofNullable(error); }
}
