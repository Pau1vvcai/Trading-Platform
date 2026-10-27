package com.cai.trading.infrastructure;

import com.cai.trading.application.AccountRepository;
import com.cai.trading.application.UserAccountService;
import com.cai.trading.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 模拟资产注入入口，与正常交易结算分开，避免破坏守恒测试的边界。 */
public final class JdbcUserAccountService implements UserAccountService {
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final TransactionTemplate transactions;

    public JdbcUserAccountService(JdbcTemplate jdbc, AccountRepository accounts,
                                  TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.accounts = Objects.requireNonNull(accounts);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public UserId createUser(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 100) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Invalid display name");
        }
        UserId id = new UserId(UUID.randomUUID());
        jdbc.update("INSERT INTO users(id, display_name) VALUES (?, ?)", id.value(), displayName.trim());
        return id;
    }

    @Override
    public Account createAccount(UserId userId, String currency, Money initialCash) {
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class,
                userId.value()) == 0) {
            throw new DomainException(ErrorCode.ORDER_NOT_FOUND, "User not found");
        }
        Account account = new Account(new AccountId(UUID.randomUUID()), userId,
                initialCash, new Money(BigDecimal.ZERO, currency), Map.of(), 0L);
        accounts.save(account);
        return account;
    }

    @Override
    public Account getAccount(AccountId accountId) {
        return accounts.findById(accountId).orElseThrow(() ->
                new DomainException(ErrorCode.ORDER_NOT_FOUND, "Account not found"));
    }

    @Override
    public Account creditPosition(AccountId accountId, Symbol symbol, Quantity quantity) {
        Account result = transactions.execute(status -> {
            Account account = getAccount(accountId);
            account.position(symbol).credit(quantity);
            accounts.save(account);
            return account;
        });
        return Objects.requireNonNull(result);
    }
}
