package com.cai.trading.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 订单聚合根。状态只能通过 fill/cancel 等领域行为变化。 */
public final class Order {
    private final OrderId id;
    private final AccountId accountId;
    private final Symbol symbol;
    private final Side side;
    private final OrderType type;
    private final Price limitPrice;
    private final Quantity originalQuantity;
    private final Instant acceptedAt;
    private final long sequence;

    private Quantity filledQuantity;
    private OrderStatus status;
    private Money spendingLimit;
    private Money reservedCash;

    public Order(OrderId id, AccountId accountId, Symbol symbol, Side side, OrderType type,
                 Price limitPrice, Quantity originalQuantity, Instant acceptedAt, long sequence) {
        this(id, accountId, symbol, side, type, limitPrice, originalQuantity,
                acceptedAt, sequence, null);
    }

    private Order(OrderId id, AccountId accountId, Symbol symbol, Side side, OrderType type,
                  Price limitPrice, Quantity originalQuantity, Instant acceptedAt, long sequence,
                  Money spendingLimit) {
        this.id = Objects.requireNonNull(id, "order id");
        this.accountId = Objects.requireNonNull(accountId, "account id");
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.side = Objects.requireNonNull(side, "side");
        this.type = Objects.requireNonNull(type, "order type");
        this.originalQuantity = Objects.requireNonNull(originalQuantity, "quantity");
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "accepted time");
        if (originalQuantity.isZero()) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Order quantity must be positive");
        }
        if (sequence <= 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Acceptance sequence must be positive");
        }
        if (type == OrderType.LIMIT && limitPrice == null) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Limit order requires a price");
        }
        if (type == OrderType.MARKET && limitPrice != null) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Market order cannot have a limit price");
        }
        if (spendingLimit != null && (type != OrderType.MARKET || side != Side.BUY)) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Only a market buy can have maxSpend");
        }
        this.limitPrice = limitPrice;
        this.sequence = sequence;
        this.filledQuantity = Quantity.zero();
        this.status = OrderStatus.NEW;
        this.spendingLimit = spendingLimit;
    }

    public static Order limit(OrderId id, AccountId accountId, Symbol symbol, Side side,
                              Price price, Quantity quantity, Instant acceptedAt, long sequence) {
        return new Order(id, accountId, symbol, side, OrderType.LIMIT,
                price, quantity, acceptedAt, sequence);
    }

    public static Order market(OrderId id, AccountId accountId, Symbol symbol, Side side,
                               Quantity quantity, Instant acceptedAt, long sequence) {
        return new Order(id, accountId, symbol, side, OrderType.MARKET,
                null, quantity, acceptedAt, sequence);
    }

    /** 应用层使用的市价买单工厂；maxSpend 用于撮合时限制最大支出。 */
    public static Order marketBuy(OrderId id, AccountId accountId, Symbol symbol,
                                  Quantity quantity, Money maxSpend, Instant acceptedAt, long sequence) {
        Objects.requireNonNull(maxSpend, "max spend");
        if (maxSpend.isZero()) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "maxSpend must be positive");
        }
        return new Order(id, accountId, symbol, Side.BUY, OrderType.MARKET,
                null, quantity, acceptedAt, sequence, maxSpend);
    }

    /** 从数据库恢复权威状态；恢复不会重新触发撮合行为。 */
    public static Order restore(OrderId id, AccountId accountId, Symbol symbol, Side side,
                                OrderType type, Price limitPrice, Quantity originalQuantity,
                                Quantity filledQuantity, OrderStatus status, Instant acceptedAt,
                                long sequence, Money spendingLimit, Money reservedCash) {
        Order order = new Order(id, accountId, symbol, side, type, limitPrice,
                originalQuantity, acceptedAt, sequence, spendingLimit);
        Objects.requireNonNull(filledQuantity, "filled quantity");
        Objects.requireNonNull(status, "status");
        if (filledQuantity.compareTo(originalQuantity) > 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Filled quantity exceeds original quantity");
        }
        order.filledQuantity = filledQuantity;
        order.status = status;
        order.reservedCash = reservedCash;
        return order;
    }

    public void fill(Quantity executedQuantity) {
        Objects.requireNonNull(executedQuantity, "executed quantity");
        ensureActive();
        if (executedQuantity.isZero() || executedQuantity.compareTo(remainingQuantity()) > 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Invalid execution quantity");
        }
        filledQuantity = filledQuantity.add(executedQuantity);
        status = filledQuantity.compareTo(originalQuantity) == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        ensureActive();
        status = OrderStatus.CANCELED;
    }

    public OrderId id() { return id; }
    public AccountId accountId() { return accountId; }
    public Symbol symbol() { return symbol; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public Optional<Price> limitPrice() { return Optional.ofNullable(limitPrice); }
    public Quantity originalQuantity() { return originalQuantity; }
    public Quantity filledQuantity() { return filledQuantity; }
    public Quantity remainingQuantity() { return originalQuantity.subtract(filledQuantity); }
    public OrderStatus status() { return status; }
    public Instant acceptedAt() { return acceptedAt; }
    public long sequence() { return sequence; }
    public boolean isActive() { return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED; }
    public Optional<Money> spendingLimit() { return Optional.ofNullable(spendingLimit); }
    public Optional<Money> reservedCash() { return Optional.ofNullable(reservedCash); }

    /** 冻结额跟随订单保存，撤单时无需猜测某账户的哪部分冻结属于该订单。 */
    public void reserveCash(Money amount) {
        if (side != Side.BUY || reservedCash != null) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Cash reservation is not allowed");
        }
        reservedCash = Objects.requireNonNull(amount, "reserved cash");
    }

    public void consumeReservedCash(Money amount) {
        if (reservedCash == null) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Order has no cash reservation");
        }
        reservedCash = reservedCash.subtract(amount);
    }

    public Money releaseReservedCash() {
        if (reservedCash == null) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Order has no cash reservation");
        }
        Money released = reservedCash;
        reservedCash = new Money(java.math.BigDecimal.ZERO, released.currency());
        return released;
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new DomainException(ErrorCode.ORDER_NOT_CANCELABLE, "Order is already terminal: " + status);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Order order && id.equals(order.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

