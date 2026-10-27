package com.cai.trading.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 数量值对象：固定 8 位精度，允许零但不允许负数。 */
public record Quantity(BigDecimal value) implements Comparable<Quantity> {
    public static final int SCALE = 8;

    public Quantity {
        Objects.requireNonNull(value, "quantity");
        try {
            value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Quantity has more than " + SCALE + " decimals");
        }
        if (value.signum() < 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Quantity cannot be negative");
        }
    }

    public static Quantity zero() {
        return new Quantity(BigDecimal.ZERO);
    }

    public Quantity add(Quantity other) {
        return new Quantity(value.add(require(other).value));
    }

    public Quantity subtract(Quantity other) {
        BigDecimal result = value.subtract(require(other).value);
        if (result.signum() < 0) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Quantity cannot become negative");
        }
        return new Quantity(result);
    }

    public Quantity min(Quantity other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    @Override
    public int compareTo(Quantity other) {
        return value.compareTo(require(other).value);
    }

    private static Quantity require(Quantity quantity) {
        return Objects.requireNonNull(quantity, "quantity");
    }
}

