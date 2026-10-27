package com.cai.trading.application;

import com.cai.trading.domain.Symbol;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 每个 Symbol 使用一个有界单线程队列：同一标的严格串行，不同标的可以并行。
 * 有界队列让过载显式失败，而不是一直吃掉堆内存。
 */
public final class PartitionedSymbolCommandProcessor implements SymbolCommandProcessor {
    private static final int QUEUE_CAPACITY = 10_000;
    private final ConcurrentMap<Symbol, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicInteger threadSequence = new AtomicInteger();

    @Override
    public <T> CompletionStage<T> submit(Symbol symbol, Supplier<T> command) {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(command);
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!accepting.get()) {
            result.completeExceptionally(new RejectedExecutionException("Processor is shutting down"));
            return result;
        }

        ThreadPoolExecutor executor = executors.computeIfAbsent(symbol, this::newExecutor);
        try {
            executor.execute(() -> {
                try {
                    result.complete(command.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    @Override
    public void shutdownGracefully(Duration timeout) {
        accepting.set(false);
        executors.values().forEach(ExecutorService::shutdown);
        long deadline = System.nanoTime() + timeout.toNanos();
        for (ExecutorService executor : executors.values()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                executor.shutdownNow();
                continue;
            }
            try {
                if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    private ThreadPoolExecutor newExecutor(Symbol symbol) {
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task,
                    "matching-" + symbol.value().replace('/', '-') + "-" + threadSequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory, new ThreadPoolExecutor.AbortPolicy());
    }
}
