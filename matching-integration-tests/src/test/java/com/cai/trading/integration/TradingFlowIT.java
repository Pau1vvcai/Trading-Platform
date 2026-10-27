package com.cai.trading.integration;

import com.cai.trading.application.*;
import com.cai.trading.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 不依赖外部服务的阶段一交易流验收测试。 */
class TradingFlowIT {
    private static final Symbol BTC_USD = new Symbol("BTC/USD");
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new Fixture();
    }

    @Test
    void should_create_two_users_and_accounts() {
        Account buyer = fixture.account("buyer", "100", "0");
        Account seller = fixture.account("seller", "0", "5");
        assertNotEquals(buyer.ownerId(), seller.ownerId());
        assertEquals(2, fixture.accounts.values.size());
    }

    @Test
    void should_deposit_simulated_cash_and_positions() {
        Account account = fixture.account("alice", "100.25", "3.5");
        assertMoney("100.25", account.availableCash());
        assertQuantity("3.5", account.position(BTC_USD).available());
        assertTrue(account.frozenCash().isZero());
    }

    @Test
    void should_match_sell_and_buy_at_same_price() {
        Account buyer = fixture.account("buyer", "100", "0");
        Account seller = fixture.account("seller", "0", "2");
        PlaceOrderResult sell = fixture.limit("sell-1", seller, Side.SELL, "10", "2");
        PlaceOrderResult buy = fixture.limit("buy-1", buyer, Side.BUY, "10", "2");
        assertEquals(OrderStatus.FILLED, fixture.order(sell).status());
        assertEquals(OrderStatus.FILLED, fixture.order(buy).status());
        assertEquals(1, buy.trades().size());
        assertMoney("80", buyer.availableCash());
        assertQuantity("2", buyer.position(BTC_USD).available());
        assertMoney("20", seller.availableCash());
    }

    @Test
    void should_settle_at_maker_price_and_release_improvement() {
        Account buyer = fixture.account("buyer", "100", "0");
        Account seller = fixture.account("seller", "0", "2");
        fixture.limit("maker-sell", seller, Side.SELL, "8", "2");
        PlaceOrderResult buy = fixture.limit("taker-buy", buyer, Side.BUY, "10", "2");
        assertEquals(new BigDecimal("8.00000000"), buy.trades().getFirst().executionPrice().value());
        assertMoney("84", buyer.availableCash());
        assertTrue(buyer.frozenCash().isZero());
        assertMoney("16", seller.availableCash());
    }

    @Test
    void should_create_multiple_trades_for_one_large_order() {
        Account buyer = fixture.account("buyer", "100", "0");
        Account seller1 = fixture.account("seller-1", "0", "1");
        Account seller2 = fixture.account("seller-2", "0", "1");
        fixture.limit("sell-1", seller1, Side.SELL, "9", "1");
        fixture.limit("sell-2", seller2, Side.SELL, "10", "1");
        PlaceOrderResult buy = fixture.limit("buy", buyer, Side.BUY, "10", "2");
        assertEquals(2, buy.trades().size());
        assertEquals(new BigDecimal("9.00000000"), buy.trades().get(0).executionPrice().value());
        assertEquals(new BigDecimal("10.00000000"), buy.trades().get(1).executionPrice().value());
        assertQuantity("2", buyer.position(BTC_USD).available());
    }

    @Test
    void should_release_remaining_assets_after_partial_fill_and_cancel() {
        Account buyer = fixture.account("buyer", "100", "0");
        Account seller = fixture.account("seller", "0", "5");
        PlaceOrderResult sell = fixture.limit("sell", seller, Side.SELL, "10", "5");
        fixture.limit("buy", buyer, Side.BUY, "10", "2");
        fixture.service.cancel(new CancelOrderCommand(seller.id(), sell.orderId(), BTC_USD));
        assertQuantity("3", seller.position(BTC_USD).available());
        assertTrue(seller.position(BTC_USD).frozen().isZero());
        assertEquals(OrderStatus.CANCELED, fixture.order(sell).status());
    }

    @Test
    void should_reject_buy_when_balance_is_insufficient() {
        Account buyer = fixture.account("buyer", "9", "0");
        DomainException error = assertThrows(DomainException.class,
                () -> fixture.limit("too-expensive", buyer, Side.BUY, "10", "1"));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, error.code());
        assertTrue(fixture.orders.values.isEmpty());
    }

    @Test
    void should_reject_sell_when_position_is_insufficient() {
        Account seller = fixture.account("seller", "0", "0.5");
        DomainException error = assertThrows(DomainException.class,
                () -> fixture.limit("too-large", seller, Side.SELL, "10", "1"));
        assertEquals(ErrorCode.INSUFFICIENT_POSITION, error.code());
        assertTrue(fixture.orders.values.isEmpty());
    }

    @Test
    void should_create_only_one_order_for_duplicate_request() {
        Account buyer = fixture.account("buyer", "100", "0");
        PlaceOrderCommand command = fixture.limitCommand("same-key", buyer, Side.BUY, "10", "1");
        PlaceOrderResult first = fixture.service.place(command);
        PlaceOrderResult second = fixture.service.place(command);
        assertEquals(first.orderId(), second.orderId());
        assertEquals(1, fixture.orders.values.size());
    }

    @Test
    void should_restore_book_and_continue_matching_after_restart() {
        Account buyer = fixture.account("buyer", "100", "0");
        Account seller = fixture.account("seller", "0", "1");
        fixture.limit("resting-sell", seller, Side.SELL, "10", "1");
        fixture.restartAndRecover();
        PlaceOrderResult buy = fixture.limit("buy-after-restart", buyer, Side.BUY, "10", "1");
        assertEquals(1, buy.trades().size());
        assertEquals(OrderStatus.FILLED, fixture.order(buy).status());
        assertTrue(fixture.service.getOrderBook(BTC_USD).asks().isEmpty());
    }

    private static void assertMoney(String expected, Money actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.amount()));
    }

    private static void assertQuantity(String expected, Quantity actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.value()));
    }

    /** 用内存适配器连接真实应用服务，验证完整业务链。 */
    private static final class Fixture {
        private final InMemoryOrders orders = new InMemoryOrders();
        private final InMemoryAccounts accounts = new InMemoryAccounts();
        private final InMemoryTrades trades = new InMemoryTrades();
        private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        private MatchingEngineRegistry engines = new InMemoryMatchingEngineRegistry(clock);
        private OrderApplicationService service = newService();

        Account account(String name, String cash, String btc) {
            Account account = new Account(
                    new AccountId(UUID.nameUUIDFromBytes(("account:" + name).getBytes())),
                    new UserId(UUID.nameUUIDFromBytes(("user:" + name).getBytes())),
                    new Money(new BigDecimal(cash), "USD"), new Money(BigDecimal.ZERO, "USD"),
                    Map.of(BTC_USD, new Position(BTC_USD,
                            new Quantity(new BigDecimal(btc)), Quantity.zero())), 0);
            accounts.save(account);
            return account;
        }

        PlaceOrderResult limit(String key, Account account, Side side, String price, String quantity) {
            return service.place(limitCommand(key, account, side, price, quantity));
        }

        PlaceOrderCommand limitCommand(String key, Account account, Side side,
                                       String price, String quantity) {
            return new PlaceOrderCommand(key, account.id(), BTC_USD, side, OrderType.LIMIT,
                    Optional.of(new Price(new BigDecimal(price))),
                    new Quantity(new BigDecimal(quantity)), Optional.empty());
        }

        Order order(PlaceOrderResult result) {
            return service.get(result.orderId());
        }

        void restartAndRecover() {
            engines = new InMemoryMatchingEngineRegistry(clock);
            OrderBookRecoveryService recovery = new OrderBookRecoveryService(orders, engines);
            recovery.recover(orders.findActiveSymbols());
            assertTrue(recovery.isReady());
            service = newService();
        }

        private OrderApplicationService newService() {
            TransactionRunner directTransactions = new TransactionRunner() {
                @Override
                public <T> T inTransaction(java.util.function.Supplier<T> action) {
                    return action.get();
                }
            };
            return new DefaultOrderApplicationService(orders, accounts, trades,
                    new DefaultRiskChecker(), new DefaultSettlementService(),
                    new InMemoryIdempotencyStore(), directTransactions, engines, clock);
        }
    }

    private static final class InMemoryOrders implements OrderRepository {
        private final Map<OrderId, Order> values = new LinkedHashMap<>();
        @Override public Optional<Order> findById(OrderId id) { return Optional.ofNullable(values.get(id)); }
        @Override public List<Order> findActiveBySymbolInSequenceOrder(Symbol symbol) {
            return values.values().stream().filter(Order::isActive)
                    .filter(order -> order.symbol().equals(symbol))
                    .sorted(Comparator.comparingLong(Order::sequence)).toList();
        }
        @Override public List<Symbol> findActiveSymbols() {
            return values.values().stream().filter(Order::isActive).map(Order::symbol).distinct().toList();
        }
        @Override public void save(Order order) { values.put(order.id(), order); }
        @Override public void saveAll(List<Order> orders) { orders.forEach(this::save); }
    }

    private static final class InMemoryAccounts implements AccountRepository {
        private final Map<AccountId, Account> values = new LinkedHashMap<>();
        @Override public Optional<Account> findById(AccountId id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(Account account) { values.put(account.id(), account); }
    }

    private static final class InMemoryTrades implements TradeRepository {
        private final List<Trade> values = new ArrayList<>();
        @Override public void saveAll(List<Trade> trades) { values.addAll(trades); }
        @Override public List<Trade> findBySymbol(Symbol symbol) {
            return values.stream().filter(trade -> trade.symbol().equals(symbol)).toList();
        }
    }
}

