package com.cai.trading.infrastructure;

import com.cai.trading.application.AccountRepository;
import com.cai.trading.domain.Account;
import com.cai.trading.domain.AccountId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL 账户/持仓映射，并通过 version 字段检测并发写冲突。 */
public final class PostgresAccountRepository implements AccountRepository {
    private final JdbcTemplate jdbc;

    public PostgresAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jdbc.query("""
                SELECT id, user_id, currency, available_cash, frozen_cash, version
                FROM accounts WHERE id = ?
                """, (rs, rowNum) -> {
            Map<com.cai.trading.domain.Symbol, com.cai.trading.domain.Position> positions = new HashMap<>();
            jdbc.query("""
                    SELECT symbol, available_quantity, frozen_quantity
                    FROM positions WHERE account_id = ?
                    """, positionRs -> {
                com.cai.trading.domain.Symbol symbol = new com.cai.trading.domain.Symbol(
                        positionRs.getString("symbol"));
                positions.put(symbol, new com.cai.trading.domain.Position(symbol,
                        new com.cai.trading.domain.Quantity(positionRs.getBigDecimal("available_quantity")),
                        new com.cai.trading.domain.Quantity(positionRs.getBigDecimal("frozen_quantity"))));
            }, id.value());
            String currency = rs.getString("currency");
            return new Account(new AccountId(rs.getObject("id", UUID.class)),
                    new com.cai.trading.domain.UserId(rs.getObject("user_id", UUID.class)),
                    new com.cai.trading.domain.Money(rs.getBigDecimal("available_cash"), currency),
                    new com.cai.trading.domain.Money(rs.getBigDecimal("frozen_cash"), currency),
                    positions, rs.getLong("version"));
        }, id.value()).stream().findFirst();
    }

    @Override
    public void save(Account account) {
        int inserted = jdbc.update("""
                INSERT INTO accounts(id, user_id, currency, available_cash, frozen_cash, version)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, account.id().value(), account.ownerId().value(), account.availableCash().currency(),
                account.availableCash().amount(), account.frozenCash().amount(), account.version());
        if (inserted == 0) {
            int updated = jdbc.update("""
                    UPDATE accounts
                    SET available_cash = ?, frozen_cash = ?, version = version + 1
                    WHERE id = ? AND version = ?
                    """, account.availableCash().amount(), account.frozenCash().amount(),
                    account.id().value(), account.version());
            if (updated != 1) {
                throw new org.springframework.dao.OptimisticLockingFailureException(
                        "Concurrent account update: " + account.id().value());
            }
            account.markPersisted(account.version() + 1);
        }

        // v0.1 用“删除后重建”保持代码直观；数据量增长后可改为差量 upsert。
        jdbc.update("DELETE FROM positions WHERE account_id = ?", account.id().value());
        account.positions().values().forEach(position -> jdbc.update("""
                INSERT INTO positions(account_id, symbol, available_quantity, frozen_quantity)
                VALUES (?, ?, ?, ?)
                """, account.id().value(), position.symbol().value(),
                position.available().value(), position.frozen().value()));
    }
}

