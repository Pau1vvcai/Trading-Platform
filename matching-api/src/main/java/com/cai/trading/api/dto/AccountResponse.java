package com.cai.trading.api.dto;

import com.cai.trading.domain.Account;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record AccountResponse(
        UUID accountId,
        UUID userId,
        String currency,
        BigDecimal availableCash,
        BigDecimal frozenCash,
        Map<String, PositionBalance> positions,
        long version) {

    public static AccountResponse from(Account account) {
        Map<String, PositionBalance> positions = account.positions().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().value(),
                        entry -> new PositionBalance(entry.getValue().available().value(),
                                entry.getValue().frozen().value())));
        return new AccountResponse(account.id().value(), account.ownerId().value(),
                account.availableCash().currency(), account.availableCash().amount(),
                account.frozenCash().amount(), positions, account.version());
    }

    public record PositionBalance(BigDecimal available, BigDecimal frozen) { }
}

