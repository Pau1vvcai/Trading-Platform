package com.cai.trading.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 模拟账户聚合根，维护现金、持仓及持久化乐观锁版本。 */
public final class Account {
    private final AccountId id;
    private final UserId ownerId;
    private Money availableCash;
    private Money frozenCash;
    private final Map<Symbol, Position> positions;
    private long version;

    public Account(AccountId id, UserId ownerId, Money availableCash, Money frozenCash,
                   Map<Symbol, Position> positions, long version) {
        this.id = Objects.requireNonNull(id, "account id");
        this.ownerId = Objects.requireNonNull(ownerId, "owner id");
        this.availableCash = Objects.requireNonNull(availableCash, "available cash");
        this.frozenCash = Objects.requireNonNull(frozenCash, "frozen cash");
        if (!availableCash.currency().equals(frozenCash.currency())) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH, "Account cash currencies differ");
        }
        if (version < 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Account version cannot be negative");
        }
        this.positions = new HashMap<>(Objects.requireNonNull(positions, "positions"));
        this.version = version;
    }

    public void freezeCash(Money amount) {
        requirePositive(amount);
        if (availableCash.compareTo(amount) < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient available cash");
        }
        availableCash = availableCash.subtract(amount);
        frozenCash = frozenCash.add(amount);
    }

    public void releaseCash(Money amount) {
        requireFrozen(amount);
        frozenCash = frozenCash.subtract(amount);
        availableCash = availableCash.add(amount);
    }

    public void debitFrozenCash(Money amount) {
        requireFrozen(amount);
        frozenCash = frozenCash.subtract(amount);
    }

    public void creditCash(Money amount) {
        requirePositive(amount);
        availableCash = availableCash.add(amount);
    }

    /** 缺失标的按零持仓创建，便于买方第一次成交后入账。 */
    public Position position(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        return positions.computeIfAbsent(symbol,
                key -> new Position(key, Quantity.zero(), Quantity.zero()));
    }

    public Map<Symbol, Position> positions() { return Map.copyOf(positions); }
    public AccountId id() { return id; }
    public UserId ownerId() { return ownerId; }
    public Money availableCash() { return availableCash; }
    public Money frozenCash() { return frozenCash; }
    public long version() { return version; }

    /** Repository 在乐观锁更新成功后推进版本；业务代码不能任意改版本。 */
    public void markPersisted(long newVersion) {
        if (newVersion != version + 1) {
            throw new IllegalArgumentException("Account version must advance by exactly one");
        }
        version = newVersion;
    }

    private void requireFrozen(Money amount) {
        requirePositive(amount);
        if (frozenCash.compareTo(amount) < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient frozen cash");
        }
    }

    private void requirePositive(Money amount) {
        Objects.requireNonNull(amount, "money");
        // compareTo simultaneously verifies that the currency matches the account.
        availableCash.compareTo(amount);
        if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Money must be positive");
        }
    }
}
