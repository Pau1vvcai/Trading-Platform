package com.cai.trading.infrastructure;

import com.cai.trading.application.TradeRepository;
import com.cai.trading.domain.Symbol;
import com.cai.trading.domain.Trade;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

public final class PostgresTradeRepository implements TradeRepository {
    private final JdbcTemplate jdbc;

    public PostgresTradeRepository(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    @Override
    public void saveAll(List<Trade> trades) {
        trades.forEach(trade -> jdbc.update("""
                INSERT INTO trades(id, symbol, maker_order_id, taker_order_id,
                                   buyer_account_id, seller_account_id, price, quantity, executed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, trade.id().value(), trade.symbol().value(), trade.makerOrderId().value(),
                trade.takerOrderId().value(), trade.buyerAccountId().value(),
                trade.sellerAccountId().value(), trade.executionPrice().value(),
                trade.executionQuantity().value(), java.sql.Timestamp.from(trade.executedAt())));
    }

    @Override
    public List<Trade> findBySymbol(Symbol symbol) {
        return jdbc.query("""
                SELECT * FROM trades WHERE symbol = ? ORDER BY executed_at, id
                """, (rs, rowNum) -> new Trade(
                new com.cai.trading.domain.TradeId(rs.getObject("id", UUID.class)),
                new Symbol(rs.getString("symbol")),
                new com.cai.trading.domain.OrderId(rs.getObject("maker_order_id", UUID.class)),
                new com.cai.trading.domain.OrderId(rs.getObject("taker_order_id", UUID.class)),
                new com.cai.trading.domain.AccountId(rs.getObject("buyer_account_id", UUID.class)),
                new com.cai.trading.domain.AccountId(rs.getObject("seller_account_id", UUID.class)),
                new com.cai.trading.domain.Price(rs.getBigDecimal("price")),
                new com.cai.trading.domain.Quantity(rs.getBigDecimal("quantity")),
                rs.getTimestamp("executed_at").toInstant()), symbol.value());
    }
}

