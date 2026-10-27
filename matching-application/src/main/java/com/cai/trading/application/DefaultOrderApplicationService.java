package com.cai.trading.application;

import com.cai.trading.domain.*;
import com.cai.trading.engine.MatchResult;
import com.cai.trading.engine.OrderUpdate;
import com.cai.trading.engine.OrderBookSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** 下单与撤单用例编排：风控、冻结、撮合、结算和持久化位于同一事务边界。 */
public final class DefaultOrderApplicationService implements OrderApplicationService {
    private final OrderRepository orders;
    private final AccountRepository accounts;
    private final TradeRepository trades;
    private final RiskChecker riskChecker;
    private final SettlementService settlement;
    private final IdempotencyStore idempotency;
    private final TransactionRunner transactions;
    private final MatchingEngineRegistry engines;
    private final Clock clock;
    private final TradingEventPublisher eventPublisher;
    private final ConcurrentMap<Symbol, AtomicLong> orderSequences = new ConcurrentHashMap<>();

    public DefaultOrderApplicationService(OrderRepository orders, AccountRepository accounts,
                                          TradeRepository trades, RiskChecker riskChecker,
                                          SettlementService settlement, IdempotencyStore idempotency,
                                          TransactionRunner transactions, MatchingEngineRegistry engines) {
        this(orders, accounts, trades, riskChecker, settlement, idempotency,
                transactions, engines, Clock.systemUTC(), TradingEventPublisher.noop());
    }

    public DefaultOrderApplicationService(OrderRepository orders, AccountRepository accounts,
                                          TradeRepository trades, RiskChecker riskChecker,
                                          SettlementService settlement, IdempotencyStore idempotency,
                                          TransactionRunner transactions, MatchingEngineRegistry engines,
                                          Clock clock) {
        this(orders, accounts, trades, riskChecker, settlement, idempotency,
                transactions, engines, clock, TradingEventPublisher.noop());
    }

    public DefaultOrderApplicationService(OrderRepository orders, AccountRepository accounts,
                                          TradeRepository trades, RiskChecker riskChecker,
                                          SettlementService settlement, IdempotencyStore idempotency,
                                          TransactionRunner transactions, MatchingEngineRegistry engines,
                                          Clock clock, TradingEventPublisher eventPublisher) {
        this.orders = Objects.requireNonNull(orders);
        this.accounts = Objects.requireNonNull(accounts);
        this.trades = Objects.requireNonNull(trades);
        this.riskChecker = Objects.requireNonNull(riskChecker);
        this.settlement = Objects.requireNonNull(settlement);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.transactions = Objects.requireNonNull(transactions);
        this.engines = Objects.requireNonNull(engines);
        this.clock = Objects.requireNonNull(clock);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public PlaceOrderResult place(PlaceOrderCommand command) {
        Objects.requireNonNull(command, "command");
        String digest = digest(command);
        PlaceOrderResult result = transactions.inTransaction(() -> idempotency.executeOnce(
                command.accountId(), command.idempotencyKey(), digest, PlaceOrderResult.class,
                () -> doPlace(command)));
        // 事件必须在数据库事务成功后发布，避免客户端看到最终会回滚的“幽灵成交”。
        // taker 与所有 maker 都可能发生状态变化；去重后逐一发布最终持久化状态。
        LinkedHashSet<OrderId> changedOrderIds = new LinkedHashSet<>();
        changedOrderIds.add(result.orderId());
        result.trades().forEach(trade -> {
            changedOrderIds.add(trade.makerOrderId());
            changedOrderIds.add(trade.takerOrderId());
        });
        changedOrderIds.forEach(id -> eventPublisher.orderChanged(get(id)));
        eventPublisher.tradesCreated(result.trades());
        return result;
    }

    private PlaceOrderResult doPlace(PlaceOrderCommand command) {
        Account incomingAccount = requireAccount(command.accountId());
        synchronized (incomingAccount) {
            riskChecker.check(incomingAccount, command);
            Order incoming = createOrder(command);
            settlement.freezeFor(incoming, incomingAccount, command);
            orders.save(incoming);
            accounts.save(incomingAccount);

            MatchResult result = engines.engineFor(command.symbol()).submit(incoming);
            Map<OrderId, Order> changedOrders = materializeUpdates(incoming, result.orderUpdates());
            Map<AccountId, Account> changedAccounts = new HashMap<>();
            changedAccounts.put(incomingAccount.id(), incomingAccount);

            for (Trade trade : result.trades()) {
                Order maker = changedOrders.get(trade.makerOrderId());
                Order taker = changedOrders.get(trade.takerOrderId());
                Account makerAccount = changedAccounts.computeIfAbsent(
                        maker.accountId(), this::requireAccount);
                Account takerAccount = changedAccounts.computeIfAbsent(
                        taker.accountId(), this::requireAccount);
                settlement.settle(trade, maker, taker, makerAccount, takerAccount);
            }

            // 市价单撤余量和自成交保护都可能留下尚未释放的订单级预留。
            for (Order order : changedOrders.values()) {
                boolean hasCashToRelease = order.reservedCash().filter(value -> !value.isZero()).isPresent();
                boolean hasPositionToRelease = order.side() == Side.SELL
                        && order.status() == OrderStatus.CANCELED
                        && !order.remainingQuantity().isZero();
                if (!order.isActive() && (hasCashToRelease || hasPositionToRelease)) {
                    Account account = changedAccounts.computeIfAbsent(order.accountId(), this::requireAccount);
                    settlement.releaseAfterCancel(order, account);
                }
            }

            orders.saveAll(List.copyOf(changedOrders.values()));
            changedAccounts.values().forEach(accounts::save);
            trades.saveAll(result.trades());
            return new PlaceOrderResult(incoming.id(), incoming.status(), result.trades());
        }
    }

    @Override
    public Order cancel(CancelOrderCommand command) {
        Objects.requireNonNull(command, "command");
        Order canceled = transactions.inTransaction(() -> {
            Order order = get(command.orderId());
            if (!order.accountId().equals(command.accountId()) || !order.symbol().equals(command.symbol())) {
                throw new DomainException(ErrorCode.ORDER_NOT_FOUND, "Order not found for account and symbol");
            }
            Account account = requireAccount(command.accountId());
            synchronized (account) {
                Order canceledOrder = engines.engineFor(command.symbol()).cancel(command.orderId());
                settlement.releaseAfterCancel(canceledOrder, account);
                orders.save(canceledOrder);
                accounts.save(account);
                return canceledOrder;
            }
        });
        eventPublisher.orderChanged(canceled);
        return canceled;
    }

    @Override
    public Order get(OrderId orderId) {
        return orders.findById(orderId).orElseThrow(() ->
                new DomainException(ErrorCode.ORDER_NOT_FOUND, "Order not found"));
    }

    @Override
    public OrderBookSnapshot getOrderBook(Symbol symbol) {
        return engines.engineFor(symbol).snapshot();
    }

    private Order createOrder(PlaceOrderCommand command) {
        long sequence = nextSequence(command.symbol());
        OrderId id = new OrderId(UUID.randomUUID());
        Instant now = Instant.now(clock);
        if (command.type() == OrderType.LIMIT) {
            return Order.limit(id, command.accountId(), command.symbol(), command.side(),
                    command.limitPrice().orElseThrow(() ->
                            new DomainException(ErrorCode.INVALID_ORDER, "Limit order requires price")),
                    command.quantity(), now, sequence);
        }
        if (command.side() == Side.BUY) {
            return Order.marketBuy(id, command.accountId(), command.symbol(), command.quantity(),
                    command.maxSpend().orElseThrow(() ->
                            new DomainException(ErrorCode.INVALID_ORDER, "Market buy requires maxSpend")),
                    now, sequence);
        }
        return Order.market(id, command.accountId(), command.symbol(), command.side(),
                command.quantity(), now, sequence);
    }

    private long nextSequence(Symbol symbol) {
        return orderSequences.computeIfAbsent(symbol, key -> {
            long currentMax = engines.engineFor(key).snapshot().bids().stream()
                    .mapToLong(Order::sequence).max().orElse(0L);
            currentMax = Math.max(currentMax, engines.engineFor(key).snapshot().asks().stream()
                    .mapToLong(Order::sequence).max().orElse(0L));
            return new AtomicLong(currentMax);
        }).incrementAndGet();
    }

    private Map<OrderId, Order> materializeUpdates(Order incoming, List<OrderUpdate> updates) {
        Map<OrderId, Order> changed = new LinkedHashMap<>();
        changed.put(incoming.id(), incoming);
        for (OrderUpdate update : updates) {
            Order order = changed.computeIfAbsent(update.orderId(), id ->
                    orders.findById(id).orElseThrow(() ->
                            new DomainException(ErrorCode.ORDER_NOT_FOUND, "Matched order is missing")));
            // 一个 taker 跨多个价位时会出现多条累计快照；只处理比当前状态更新的快照。
            if (update.filled().compareTo(order.filledQuantity()) > 0) {
                Quantity missingFill = update.filled().subtract(order.filledQuantity());
                order.fill(missingFill);
            }
            if (update.status() == OrderStatus.CANCELED && order.isActive()) {
                order.cancel();
            }
        }
        return changed;
    }

    private Account requireAccount(AccountId id) {
        return accounts.findById(id).orElseThrow(() ->
                new DomainException(ErrorCode.ORDER_NOT_FOUND, "Account not found"));
    }

    private static String digest(PlaceOrderCommand command) {
        return String.join("|", command.accountId().value().toString(), command.symbol().value(),
                command.side().name(), command.type().name(),
                command.limitPrice().map(price -> price.value().toPlainString()).orElse(""),
                command.quantity().value().toPlainString(),
                command.maxSpend().map(money -> money.amount().toPlainString() + money.currency()).orElse(""));
    }
}

