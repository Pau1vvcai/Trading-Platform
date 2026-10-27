package com.cai.trading.api.dto;

import com.cai.trading.domain.Order;
import com.cai.trading.engine.OrderBookSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderBookResponse(String symbol, List<Entry> bids, List<Entry> asks) {
    public static OrderBookResponse from(OrderBookSnapshot snapshot) {
        return new OrderBookResponse(snapshot.symbol().value(), map(snapshot.bids()), map(snapshot.asks()));
    }

    private static List<Entry> map(List<Order> orders) {
        return orders.stream().map(order -> new Entry(order.id().value(),
                order.limitPrice().orElseThrow().value(), order.remainingQuantity().value(),
                order.sequence())).toList();
    }

    public record Entry(UUID orderId, BigDecimal price, BigDecimal remainingQuantity, long sequence) { }
}

