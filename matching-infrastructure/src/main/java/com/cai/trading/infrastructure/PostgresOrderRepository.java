package com.cai.trading.infrastructure;

import com.cai.trading.application.OrderRepository;
import com.cai.trading.domain.Order;
import com.cai.trading.domain.OrderId;
import com.cai.trading.domain.Symbol;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL 订单适配器；恢复查询始终按稳定 sequence 排序。 */
public final class PostgresOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbc;

    public PostgresOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jdbc.query("SELECT * FROM orders WHERE id = ?", this::mapOrder, id.value())
                .stream().findFirst();
    }

    @Override
    public List<Order> findActiveBySymbolInSequenceOrder(Symbol symbol) {
        return jdbc.query("""
                SELECT * FROM orders
                WHERE symbol = ? AND status IN ('NEW', 'PARTIALLY_FILLED')
                ORDER BY acceptance_sequence
                """, this::mapOrder, symbol.value());
    }

    @Override
    public List<Symbol> findActiveSymbols() {
        return jdbc.query("""
                SELECT DISTINCT symbol FROM orders
                WHERE status IN ('NEW', 'PARTIALLY_FILLED')
                ORDER BY symbol
                """, (rs, rowNum) -> new Symbol(rs.getString("symbol")));
    }

    @Override
    public void save(Order order) {
        jdbc.update("""
                INSERT INTO orders(
                    id, account_id, symbol, side, order_type, limit_price,
                    original_quantity, filled_quantity, status, accepted_at,
                    acceptance_sequence, spending_limit, reserved_cash, cash_currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    filled_quantity = EXCLUDED.filled_quantity,
                    status = EXCLUDED.status,
                    reserved_cash = EXCLUDED.reserved_cash
                """, order.id().value(), order.accountId().value(), order.symbol().value(),
                order.side().name(), order.type().name(),
                order.limitPrice().map(com.cai.trading.domain.Price::value).orElse(null),
                order.originalQuantity().value(), order.filledQuantity().value(), order.status().name(),
                java.sql.Timestamp.from(order.acceptedAt()), order.sequence(),
                order.spendingLimit().map(com.cai.trading.domain.Money::amount).orElse(null),
                order.reservedCash().map(com.cai.trading.domain.Money::amount).orElse(null),
                order.spendingLimit().map(com.cai.trading.domain.Money::currency)
                        .or(() -> order.reservedCash().map(com.cai.trading.domain.Money::currency))
                        .orElse(null));
    }

    @Override
    public void saveAll(List<Order> orders) {
        orders.forEach(this::save);
    }

    private Order mapOrder(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String currency = rs.getString("cash_currency");
        java.math.BigDecimal limit = rs.getBigDecimal("limit_price");
        java.math.BigDecimal spending = rs.getBigDecimal("spending_limit");
        java.math.BigDecimal reserved = rs.getBigDecimal("reserved_cash");
        return Order.restore(
                new OrderId(rs.getObject("id", UUID.class)),
                new com.cai.trading.domain.AccountId(rs.getObject("account_id", UUID.class)),
                new Symbol(rs.getString("symbol")),
                com.cai.trading.domain.Side.valueOf(rs.getString("side")),
                com.cai.trading.domain.OrderType.valueOf(rs.getString("order_type")),
                limit == null ? null : new com.cai.trading.domain.Price(limit),
                new com.cai.trading.domain.Quantity(rs.getBigDecimal("original_quantity")),
                new com.cai.trading.domain.Quantity(rs.getBigDecimal("filled_quantity")),
                com.cai.trading.domain.OrderStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("accepted_at").toInstant(),
                rs.getLong("acceptance_sequence"),
                spending == null ? null : new com.cai.trading.domain.Money(spending, currency),
                reserved == null ? null : new com.cai.trading.domain.Money(reserved, currency));
    }
}

