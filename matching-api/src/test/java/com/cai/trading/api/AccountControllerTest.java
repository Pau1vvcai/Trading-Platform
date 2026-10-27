package com.cai.trading.api;

import com.cai.trading.application.UserAccountService;
import com.cai.trading.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AccountControllerTest {
    private StubUserAccountService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = new StubUserAccountService();
        mvc = standaloneSetup(new AccountController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void should_bind_account_id_from_path_when_crediting_position() throws Exception {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(new AccountId(accountId), new UserId(UUID.randomUUID()),
                new Money(BigDecimal.ZERO, "USD"), new Money(BigDecimal.ZERO, "USD"), Map.of(), 0);
        service.response = account;

        mvc.perform(post("/api/v1/accounts/{accountId}/positions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"BTC-USD","quantity":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));

        assertEquals(new AccountId(accountId), service.creditedAccountId);
        assertEquals(new Symbol("BTC/USD"), service.creditedSymbol);
        assertEquals(new Quantity(BigDecimal.TEN), service.creditedQuantity);
    }

    @Test
    void should_return_bad_request_for_placeholder_uuid() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"卖方用户UUID","currency":"USD","initialCash":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"))
                .andExpect(jsonPath("$.message").value("Request contains an invalid value or identifier"));
    }

    /** 手写桩避免测试依赖 JVM agent，Java 21 及更新版本都能稳定运行。 */
    private static final class StubUserAccountService implements UserAccountService {
        private Account response;
        private AccountId creditedAccountId;
        private Symbol creditedSymbol;
        private Quantity creditedQuantity;

        @Override public UserId createUser(String displayName) { throw new UnsupportedOperationException(); }
        @Override public Account createAccount(UserId userId, String currency, Money initialCash) {
            throw new UnsupportedOperationException();
        }
        @Override public Account getAccount(AccountId accountId) { throw new UnsupportedOperationException(); }
        @Override public Account creditPosition(AccountId accountId, Symbol symbol, Quantity quantity) {
            creditedAccountId = accountId; creditedSymbol = symbol; creditedQuantity = quantity;
            return response;
        }
    }
}
