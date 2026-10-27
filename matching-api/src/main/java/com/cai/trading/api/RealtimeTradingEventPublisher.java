package com.cai.trading.api;

import com.cai.trading.analytics.SequencedEventHub;
import com.cai.trading.application.TradingEventPublisher;
import com.cai.trading.domain.Order;
import com.cai.trading.domain.Trade;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;
import java.util.Map;

/** 将应用层事件转换为不泄露领域对象的 WebSocket 消息。 */
public final class RealtimeTradingEventPublisher implements TradingEventPublisher {
    private final SequencedEventHub hub;
    private final Counter counter;

    public RealtimeTradingEventPublisher(SequencedEventHub hub, MeterRegistry metrics) {
        this.hub = hub;
        this.counter = metrics.counter("trading.realtime.events");
    }

    @Override public void orderChanged(Order order) {
        hub.publish(order.symbol().value(), "ORDER_CHANGED", Map.of(
                "orderId", order.id().value().toString(), "status", order.status().name(),
                "side", order.side().name(), "filledQuantity", order.filledQuantity().value(),
                "remainingQuantity", order.remainingQuantity().value()));
        counter.increment();
    }

    @Override public void tradesCreated(List<Trade> trades) {
        trades.forEach(trade -> {
            hub.publish(trade.symbol().value(), "TRADE_CREATED", Map.of(
                    "tradeId", trade.id().value().toString(),
                    "price", trade.executionPrice().value(),
                    "quantity", trade.executionQuantity().value(),
                    "executedAt", trade.executedAt().toString()));
            counter.increment();
        });
    }
}
