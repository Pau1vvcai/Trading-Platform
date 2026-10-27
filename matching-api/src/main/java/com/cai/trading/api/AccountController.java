package com.cai.trading.api;

import com.cai.trading.api.dto.CreateAccountRequest;
import com.cai.trading.api.dto.AccountResponse;
import com.cai.trading.api.dto.PositionCreditRequest;
import com.cai.trading.application.UserAccountService;
import com.cai.trading.domain.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final UserAccountService service;

    public AccountController(UserAccountService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = service.createAccount(new UserId(request.userId()), request.currency(),
                new Money(request.initialCash(), request.currency()));
        return AccountResponse.from(account);
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@PathVariable("accountId") UUID accountId) {
        return AccountResponse.from(service.getAccount(new AccountId(accountId)));
    }

    @PostMapping("/{accountId}/positions")
    public AccountResponse creditPosition(@PathVariable("accountId") UUID accountId,
                                          @Valid @RequestBody PositionCreditRequest request) {
        Account account = service.creditPosition(new AccountId(accountId),
                new Symbol(request.symbol()), new Quantity(request.quantity()));
        return AccountResponse.from(account);
    }
}

