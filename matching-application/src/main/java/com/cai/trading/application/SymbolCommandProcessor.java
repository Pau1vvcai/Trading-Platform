package com.cai.trading.application;

import com.cai.trading.domain.Symbol;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 同一 Symbol 串行，不同 Symbol 可并行的命令入口。
 * 实现需要覆盖分区队列、背压、异常传播和“停止接收后排空队列”的优雅关闭。
 */
public interface SymbolCommandProcessor extends AutoCloseable {
    <T> CompletionStage<T> submit(Symbol symbol, Supplier<T> command);
    void shutdownGracefully(Duration timeout);

    @Override
    default void close() {
        shutdownGracefully(Duration.ofSeconds(30));
    }
}

