package com.cai.trading.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 价格值对象：输入最多 8 位小数，金额乘积按 8 位小数显式四舍五入。 */
public record Price(BigDecimal value) implements Comparable<Price> {
    public static final int SCALE = 8;

    public Price {
        Objects.requireNonNull(value, "price");
        try {
            // v0.1 对非法精度直接拒绝，避免静默改变用户的价格。
            value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Price has more than " + SCALE + " decimals");
        }
        if (value.signum() <= 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Price must be positive");
        }
    }

    @Override
    public int compareTo(Price other) {
        return value.compareTo(Objects.requireNonNull(other, "other price").value);
    }

    public Money multiply(Quantity quantity, String currency) {
        BigDecimal amount = value.multiply(Objects.requireNonNull(quantity, "quantity").value())
                .setScale(Money.SCALE, RoundingMode.HALF_UP);
        return new Money(amount, currency);
    }
}

