package com.cai.trading.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentifierTest {
    @Test
    void should_reject_null_user_id() {
        assertThrows(RuntimeException.class, () -> new UserId(null));
    }

    @Test
    void should_reject_null_account_id() {
        assertThrows(RuntimeException.class, () -> new AccountId(null));
    }

    @Test
    void should_reject_null_order_id() {
        assertThrows(RuntimeException.class, () -> new OrderId(null));
    }

    @Test
    void should_reject_null_trade_id() {
        assertThrows(RuntimeException.class, () -> new TradeId(null));
    }

    @Test
    void should_reject_null_symbol() {
        assertThrows(RuntimeException.class, () -> new Symbol(null));
    }

    @Test
    void should_reject_blank_symbol() {
        assertThrows(RuntimeException.class, () -> new Symbol("   "));
    }

    @Test
    void should_normalize_url_safe_symbol_separator() {
        assertEquals(new Symbol("BTC/USD"), new Symbol(" btc-usd "));
    }

    @Test
    void should_compare_identifier_value_objects_by_wrapped_uuid() {
        UUID value = UUID.randomUUID();
        assertAll(
                () -> assertEquals(new UserId(value), new UserId(value)),
                () -> assertEquals(new AccountId(value), new AccountId(value)),
                () -> assertEquals(new OrderId(value), new OrderId(value)),
                () -> assertEquals(new TradeId(value), new TradeId(value)));
    }
}

