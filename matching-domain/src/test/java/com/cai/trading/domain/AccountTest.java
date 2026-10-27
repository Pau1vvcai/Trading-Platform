package com.cai.trading.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.cai.trading.domain.DomainTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountTest {
    @Test
    void should_move_available_cash_to_frozen_when_reserving() {
        Account account = account("100");

        account.freezeCash(money("40"));

        assertEquals(money("60"), account.availableCash());
        assertEquals(money("40"), account.frozenCash());
    }

    @Test
    void should_reject_freeze_larger_than_available_cash() {
        Account account = account("10");
        DomainException error = assertThrows(DomainException.class,
                () -> account.freezeCash(money("11")));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, error.code());
    }

    @Test
    void should_release_frozen_cash_back_to_available() {
        Account account = new Account(new AccountId(UUID.randomUUID()), new UserId(UUID.randomUUID()),
                money("60"), money("40"), Map.of(), 0L);

        account.releaseCash(money("10"));

        assertEquals(money("70"), account.availableCash());
        assertEquals(money("30"), account.frozenCash());
    }

    @Test
    void should_debit_only_frozen_cash_during_settlement() {
        Account account = new Account(new AccountId(UUID.randomUUID()), new UserId(UUID.randomUUID()),
                money("60"), money("40"), Map.of(), 0L);

        account.debitFrozenCash(money("25"));

        assertEquals(money("60"), account.availableCash());
        assertEquals(money("15"), account.frozenCash());
    }

    @Test
    void should_credit_cash_to_available_balance() {
        Account account = account("10");
        account.creditCash(money("5"));
        assertEquals(money("15"), account.availableCash());
    }

    @Test
    void should_reject_cash_operation_in_different_currency() {
        Account account = account("10");
        Money usd = new Money(new java.math.BigDecimal("1"), "USD");
        assertThrows(DomainException.class, () -> account.freezeCash(usd));
    }

    @Test
    void should_defensively_copy_position_map() {
        Position position = new Position(SYMBOL, quantity("2"), quantity("0"));
        Map<Symbol, Position> positions = new HashMap<>();
        positions.put(SYMBOL, position);
        Account account = new Account(new AccountId(UUID.randomUUID()), new UserId(UUID.randomUUID()),
                money("0"), money("0"), positions, 0L);

        positions.clear();

        assertSame(position, account.position(SYMBOL));
    }
}
