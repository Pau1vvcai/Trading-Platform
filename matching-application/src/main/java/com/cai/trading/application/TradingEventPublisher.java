package com.cai.trading.application;

import com.cai.trading.domain.Order;
import com.cai.trading.domain.Trade;

import java.util.List;

/** 事务成功后发布交易事件的端口；基础设施可将其适配到 WebSocket、消息队列或审计日志。 */
public interface TradingEventPublisher {
    void orderChanged(Order order);
    void tradesCreated(List<Trade> trades);

    static TradingEventPublisher noop() {
        return new TradingEventPublisher() {
            @Override public void orderChanged(Order order) { }
            @Override public void tradesCreated(List<Trade> trades) { }
        };
    }
}
