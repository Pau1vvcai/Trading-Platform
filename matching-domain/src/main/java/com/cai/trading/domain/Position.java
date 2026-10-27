package com.cai.trading.domain;

import java.util.Objects;

/** 单一标的的可用/冻结持仓，所有变化都保持两者非负。 */
public final class Position {
    private final Symbol symbol;
    private Quantity available;
    private Quantity frozen;

    public Position(Symbol symbol, Quantity available, Quantity frozen) {
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.available = Objects.requireNonNull(available, "available quantity");
        this.frozen = Objects.requireNonNull(frozen, "frozen quantity");
    }

    public void freeze(Quantity quantity) {
        requirePositive(quantity);
        if (available.compareTo(quantity) < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_POSITION, "Insufficient available position");
        }
        available = available.subtract(quantity);
        frozen = frozen.add(quantity);
    }

    public void release(Quantity quantity) {
        requireFrozen(quantity);
        frozen = frozen.subtract(quantity);
        available = available.add(quantity);
    }

    public void debitFrozen(Quantity quantity) {
        requireFrozen(quantity);
        frozen = frozen.subtract(quantity);
    }

    public void credit(Quantity quantity) {
        requirePositive(quantity);
        available = available.add(quantity);
    }

    public Symbol symbol() { return symbol; }
    public Quantity available() { return available; }
    public Quantity frozen() { return frozen; }

    private void requireFrozen(Quantity quantity) {
        requirePositive(quantity);
        if (frozen.compareTo(quantity) < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_POSITION, "Insufficient frozen position");
        }
    }

    private static void requirePositive(Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.isZero()) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Quantity must be positive");
        }
    }
}

