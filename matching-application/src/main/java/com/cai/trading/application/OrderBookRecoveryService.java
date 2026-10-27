package com.cai.trading.application;

import com.cai.trading.domain.Symbol;

import java.util.Collection;

/** 启动恢复只重建挂单，不重新触发撮合；恢复完成前 API 应拒绝交易流量。 */
public final class OrderBookRecoveryService {
    private final OrderRepository orders;
    private final MatchingEngineRegistry engines;
    private volatile boolean ready;

    public OrderBookRecoveryService(OrderRepository orders, MatchingEngineRegistry engines) {
        this.orders = java.util.Objects.requireNonNull(orders);
        this.engines = java.util.Objects.requireNonNull(engines);
    }

    public void recover(Collection<Symbol> symbols) {
        ready = false;
        for (Symbol symbol : symbols) {
            for (com.cai.trading.domain.Order order : orders.findActiveBySymbolInSequenceOrder(symbol)) {
                engines.engineFor(symbol).restore(order);
            }
        }
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }
}
