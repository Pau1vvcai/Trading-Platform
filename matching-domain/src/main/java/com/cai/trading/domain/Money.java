package com.cai.trading.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/** 金额值对象：固定 8 位精度，并拒绝跨币种运算。 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {
    public static final int SCALE = 8;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (currency.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Currency cannot be blank");
        }
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Money has more than " + SCALE + " decimals");
        }
        if (amount.signum() < 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Money cannot be negative");
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = amount.subtract(other.amount);
        if (result.signum() < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_BALANCE, "Money cannot become negative");
        }
        return new Money(result, currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "money");
        if (!currency.equals(other.currency)) {
            throw new DomainException(ErrorCode.CURRENCY_MISMATCH,
                    "Cannot combine " + currency + " and " + other.currency);
        }
    }
}

