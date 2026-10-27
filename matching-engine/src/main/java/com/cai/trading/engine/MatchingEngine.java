package com.cai.trading.engine;

import com.cai.trading.domain.*;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 与 Spring、数据库、网络和系统时钟无关的纯撮合核心。
 * 公共操作同步执行，避免同一订单簿被多个请求并发破坏。
 */
public final class MatchingEngine {
    private final OrderBook orderBook;
    private final TradeFactory tradeFactory;
    private final Set<OrderId> acceptedOrderIds = new HashSet<>();

    public MatchingEngine(OrderBook orderBook, TradeFactory tradeFactory) {
        this.orderBook = Objects.requireNonNull(orderBook, "order book");
        this.tradeFactory = Objects.requireNonNull(tradeFactory, "trade factory");
        OrderBookSnapshot snapshot = orderBook.snapshot();
        snapshot.bids().forEach(order -> acceptedOrderIds.add(order.id()));
        snapshot.asks().forEach(order -> acceptedOrderIds.add(order.id()));
    }

    public synchronized MatchResult submit(Order incomingOrder) {
        Objects.requireNonNull(incomingOrder, "incoming order");
        if (!incomingOrder.symbol().equals(orderBook.symbol()) || !incomingOrder.isActive()) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Order cannot be submitted to this engine");
        }
        if (!acceptedOrderIds.add(incomingOrder.id())) {
            throw new DomainException(ErrorCode.DUPLICATE_ORDER, "Order was already submitted");
        }

        List<Trade> trades = new ArrayList<>();
        List<OrderUpdate> updates = new ArrayList<>();
        Money remainingBudget = incomingOrder.spendingLimit().orElse(null);

        while (incomingOrder.isActive() && !incomingOrder.remainingQuantity().isZero()) {
            Order maker = bestOpposite(incomingOrder.side()).orElse(null);
            if (maker == null || !crosses(incomingOrder, maker)) {
                break;
            }

            // v0.1 的自成交保护采用 cancel-taker，避免跳过队首破坏价格时间优先。
            if (maker.accountId().equals(incomingOrder.accountId())) {
                incomingOrder.cancel();
                updates.add(update(incomingOrder));
                break;
            }

            Price executionPrice = maker.limitPrice().orElseThrow();
            Quantity executionQuantity = incomingOrder.remainingQuantity().min(maker.remainingQuantity());
            if (remainingBudget != null) {
                Quantity affordable = affordableQuantity(remainingBudget, executionPrice);
                executionQuantity = executionQuantity.min(affordable);
                if (executionQuantity.isZero()) {
                    break;
                }
            }

            Trade trade = tradeFactory.create(maker, incomingOrder, executionPrice, executionQuantity);
            maker.fill(executionQuantity);
            incomingOrder.fill(executionQuantity);
            trades.add(trade);
            updates.add(update(maker));
            updates.add(update(incomingOrder));

            if (remainingBudget != null) {
                remainingBudget = remainingBudget.subtract(
                        executionPrice.multiply(executionQuantity, remainingBudget.currency()));
            }
            if (!maker.isActive()) {
                orderBook.remove(maker.id());
            }
        }

        if (incomingOrder.isActive() && !incomingOrder.remainingQuantity().isZero()) {
            if (incomingOrder.type() == OrderType.LIMIT) {
                orderBook.add(incomingOrder);
            } else {
                incomingOrder.cancel();
                updates.add(update(incomingOrder));
            }
        }
        return new MatchResult(trades, updates);
    }

    public synchronized Order cancel(OrderId orderId) {
        Order order = orderBook.remove(orderId).orElseThrow(() ->
                new DomainException(ErrorCode.ORDER_NOT_FOUND, "Active order not found"));
        order.cancel();
        return order;
    }

    public synchronized OrderBookSnapshot snapshot() {
        return orderBook.snapshot();
    }

    /** 恢复路径只重新挂入活动限价单，不运行撮合循环。 */
    public synchronized void restore(Order order) {
        Objects.requireNonNull(order, "order");
        if (!acceptedOrderIds.add(order.id())) {
            throw new DomainException(ErrorCode.DUPLICATE_ORDER, "Duplicate restored order");
        }
        orderBook.add(order);
    }

    private java.util.Optional<Order> bestOpposite(Side incomingSide) {
        return incomingSide == Side.BUY ? orderBook.bestAsk() : orderBook.bestBid();
    }

    private static boolean crosses(Order taker, Order maker) {
        if (taker.type() == OrderType.MARKET) {
            return true;
        }
        Price takerPrice = taker.limitPrice().orElseThrow();
        Price makerPrice = maker.limitPrice().orElseThrow();
        return taker.side() == Side.BUY
                ? makerPrice.compareTo(takerPrice) <= 0
                : makerPrice.compareTo(takerPrice) >= 0;
    }

    private static Quantity affordableQuantity(Money budget, Price price) {
        return new Quantity(budget.amount().divide(
                price.value(), Quantity.SCALE, RoundingMode.DOWN));
    }

    private static OrderUpdate update(Order order) {
        return new OrderUpdate(order.id(), order.status(),
                order.filledQuantity(), order.remainingQuantity());
    }
}
