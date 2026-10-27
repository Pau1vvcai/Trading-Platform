package com.cai.trading.api;

import com.cai.trading.api.dto.OrderResponse;
import com.cai.trading.api.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import com.cai.trading.application.*;
import com.cai.trading.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderApplicationService service;
    private final UserAccountService accounts;

    public OrderController(OrderApplicationService service, UserAccountService accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@RequestHeader("Idempotency-Key") String idempotencyKey,
                               @Valid @RequestBody PlaceOrderRequest request) {
        AccountId accountId = new AccountId(request.accountId());
        String currency = accounts.getAccount(accountId).availableCash().currency();
        PlaceOrderCommand command = new PlaceOrderCommand(idempotencyKey,
                accountId, new Symbol(request.symbol()),
                request.side(), request.type(),
                Optional.ofNullable(request.limitPrice()).map(Price::new),
                new Quantity(request.quantity()),
                Optional.ofNullable(request.maxSpend()).map(value -> new Money(value, currency)));
        PlaceOrderResult result = service.place(command);
        return OrderResponse.from(service.get(result.orderId()));
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable("orderId") UUID orderId) {
        return OrderResponse.from(service.get(new OrderId(orderId)));
    }

    @DeleteMapping("/{orderId}")
    public OrderResponse cancel(@PathVariable("orderId") UUID orderId) {
        Order order = service.get(new OrderId(orderId));
        return OrderResponse.from(service.cancel(
                new CancelOrderCommand(order.accountId(), order.id(), order.symbol())));
    }

}

