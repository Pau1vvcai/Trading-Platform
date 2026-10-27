package com.cai.trading.engine;

import com.cai.trading.domain.Order;
import com.cai.trading.domain.OrderId;
import com.cai.trading.domain.Price;
import com.cai.trading.domain.Side;
import com.cai.trading.domain.Symbol;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 * 单一 Symbol 的订单簿。
 * 买盘降序、卖盘升序；同价位使用 FIFO 队列，并通过订单 ID 索引支持撤单。
 */
public final class OrderBook {
    private final Symbol symbol;
    private final NavigableMap<Price, ArrayDeque<Order>> bids =
            new TreeMap<>(Collections.reverseOrder());
    private final NavigableMap<Price, ArrayDeque<Order>> asks = new TreeMap<>();
    private final Map<OrderId, Location> index = new HashMap<>();

    public OrderBook(Symbol symbol) {
        this.symbol = Objects.requireNonNull(symbol, "symbol");
    }

    public void add(Order order) {
        Objects.requireNonNull(order, "order");
        if (!symbol.equals(order.symbol()) || order.type() != com.cai.trading.domain.OrderType.LIMIT
                || !order.isActive() || order.limitPrice().isEmpty()) {
            throw new com.cai.trading.domain.DomainException(
                    com.cai.trading.domain.ErrorCode.INVALID_ORDER,
                    "Only active limit orders for " + symbol.value() + " can rest");
        }
        if (index.containsKey(order.id())) {
            throw new com.cai.trading.domain.DomainException(
                    com.cai.trading.domain.ErrorCode.DUPLICATE_ORDER, "Duplicate order id");
        }
        Price price = order.limitPrice().orElseThrow();
        levels(order.side()).computeIfAbsent(price, ignored -> new ArrayDeque<>()).addLast(order);
        index.put(order.id(), new Location(order.side(), price));
    }

    public Optional<Order> bestBid() {
        return best(bids);
    }

    public Optional<Order> bestAsk() {
        return best(asks);
    }

    public Optional<Order> remove(OrderId orderId) {
        Location location = index.remove(orderId);
        if (location == null) {
            return Optional.empty();
        }
        NavigableMap<Price, ArrayDeque<Order>> levels = levels(location.side());
        ArrayDeque<Order> queue = levels.get(location.price());
        Order found = null;
        for (Order order : queue) {
            if (order.id().equals(orderId)) {
                found = order;
                break;
            }
        }
        if (found == null || !queue.remove(found)) {
            throw new IllegalStateException("Order index and price level are inconsistent");
        }
        if (queue.isEmpty()) {
            levels.remove(location.price());
        }
        return Optional.of(found);
    }

    public List<Order> ordersAt(Price price, Side side) {
        ArrayDeque<Order> queue = levels(side).get(price);
        return queue == null ? List.of() : List.copyOf(queue);
    }

    public OrderBookSnapshot snapshot() {
        return new OrderBookSnapshot(symbol, flatten(bids), flatten(asks));
    }

    public Symbol symbol() {
        return symbol;
    }

    public boolean contains(OrderId orderId) {
        return index.containsKey(orderId);
    }

    private NavigableMap<Price, ArrayDeque<Order>> levels(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private static Optional<Order> best(NavigableMap<Price, ArrayDeque<Order>> levels) {
        return levels.isEmpty() ? Optional.empty()
                : Optional.of(levels.firstEntry().getValue().getFirst());
    }

    private static List<Order> flatten(NavigableMap<Price, ArrayDeque<Order>> levels) {
        List<Order> result = new ArrayList<>();
        levels.values().forEach(result::addAll);
        return List.copyOf(result);
    }

    private record Location(Side side, Price price) { }
}

