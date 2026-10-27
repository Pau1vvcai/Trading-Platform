package com.cai.trading.infrastructure;

import com.cai.trading.analytics.MarketBar;
import com.cai.trading.analytics.MarketDataRepository;
import com.cai.trading.domain.Quantity;
import com.cai.trading.domain.Symbol;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 使用 UPSERT 保存 K 线，因此同一份 CSV 可以安全地重复导入。 */
public final class PostgresMarketDataRepository implements MarketDataRepository {
    private final JdbcTemplate jdbc;

    public PostgresMarketDataRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void saveAll(List<MarketBar> bars) {
        jdbc.batchUpdate("""
                INSERT INTO market_bars(symbol, bar_time, open_price, high_price, low_price, close_price, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(symbol, bar_time) DO UPDATE SET
                  open_price=EXCLUDED.open_price, high_price=EXCLUDED.high_price,
                  low_price=EXCLUDED.low_price, close_price=EXCLUDED.close_price, volume=EXCLUDED.volume
                """, bars, 500, (ps, bar) -> {
            ps.setString(1, bar.symbol().value());
            ps.setTimestamp(2, Timestamp.from(bar.timestamp()));
            ps.setBigDecimal(3, bar.open()); ps.setBigDecimal(4, bar.high());
            ps.setBigDecimal(5, bar.low()); ps.setBigDecimal(6, bar.close());
            ps.setBigDecimal(7, bar.volume().value());
        });
    }

    @Override public List<MarketBar> find(Symbol symbol, Instant from, Instant to) {
        return jdbc.query("""
                SELECT symbol, bar_time, open_price, high_price, low_price, close_price, volume
                FROM market_bars WHERE symbol=? AND bar_time>=? AND bar_time<? ORDER BY bar_time
                """, (rs, row) -> new MarketBar(new Symbol(rs.getString("symbol")),
                rs.getTimestamp("bar_time").toInstant(), rs.getBigDecimal("open_price"),
                rs.getBigDecimal("high_price"), rs.getBigDecimal("low_price"),
                rs.getBigDecimal("close_price"), new Quantity(rs.getBigDecimal("volume"))),
                symbol.value(), Timestamp.from(from), Timestamp.from(to));
    }

    @Override public long count(Symbol symbol) {
        Long value = jdbc.queryForObject("SELECT count(*) FROM market_bars WHERE symbol=?", Long.class, symbol.value());
        return value == null ? 0 : value;
    }
}
