package com.cai.trading.application;

import com.cai.trading.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.cai.trading.application.ApplicationTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultRiskCheckerTest {
    private DefaultRiskChecker checker;

    @BeforeEach
    void setUp() {
        checker = new DefaultRiskChecker();
    }

    @Test
    void should_accept_limit_buy_when_cash_exactly_covers_limit_notional() {
        Account buyer = account("buyer", "50", "0");
        assertDoesNotThrow(() -> checker.check(buyer, limitCommand(buyer, Side.BUY, "10", "5")));
    }

    @Test
    void should_reject_limit_buy_when_cash_is_insufficient() {
        Account buyer = account("buyer", "49.99", "0");

        DomainException error = assertThrows(DomainException.class,
                () -> checker.check(buyer, limitCommand(buyer, Side.BUY, "10", "5")));

        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, error.code());
    }

    @Test
    void should_require_max_spend_for_market_buy() {
        Account buyer = account("buyer", "100", "0");
        PlaceOrderCommand missingCap = new PlaceOrderCommand("key", buyer.id(), SYMBOL,
                Side.BUY, OrderType.MARKET, Optional.empty(), quantity("5"), Optional.empty());

        DomainException error = assertThrows(DomainException.class, () -> checker.check(buyer, missingCap));
        assertEquals(ErrorCode.INVALID_ORDER, error.code());
    }

    @Test
    void should_reject_market_buy_when_max_spend_exceeds_available_cash() {
        Account buyer = account("buyer", "99", "0");
        DomainException error = assertThrows(DomainException.class,
                () -> checker.check(buyer, marketBuyCommand(buyer, "5", "100")));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, error.code());
    }

    @Test
    void should_accept_sell_when_position_exactly_covers_quantity() {
        Account seller = account("seller", "0", "5");
        assertDoesNotThrow(() -> checker.check(seller, limitCommand(seller, Side.SELL, "10", "5")));
    }

    @Test
    void should_reject_sell_when_position_is_insufficient() {
        Account seller = account("seller", "0", "4");
        DomainException error = assertThrows(DomainException.class,
                () -> checker.check(seller, limitCommand(seller, Side.SELL, "10", "5")));
        assertEquals(ErrorCode.INSUFFICIENT_POSITION, error.code());
    }
}

