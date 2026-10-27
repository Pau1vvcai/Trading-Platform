package com.cai.trading.application;

import com.cai.trading.domain.Account;
import com.cai.trading.domain.*;

import java.util.Objects;

public final class DefaultRiskChecker implements RiskChecker {
    @Override
    public void check(Account account, PlaceOrderCommand command) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(command, "command");
        if (!account.id().equals(command.accountId())) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Command account does not match loaded account");
        }

        if (command.side() == Side.BUY) {
            Money required = command.type() == OrderType.LIMIT
                    ? command.limitPrice().orElseThrow(() -> invalid("Limit buy requires price"))
                        .multiply(command.quantity(), account.availableCash().currency())
                    : command.maxSpend().orElseThrow(() -> invalid("Market buy requires maxSpend"));
            if (account.availableCash().compareTo(required) < 0) {
                throw new DomainException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient cash for order");
            }
            return;
        }

        if (account.position(command.symbol()).available().compareTo(command.quantity()) < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_POSITION, "Insufficient position for order");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(ErrorCode.INVALID_ORDER, message);
    }
}

