package com.cai.trading.api.dto;

import com.cai.trading.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.UUID;
import com.cai.trading.domain.Order;

public record OrderResponse(UUID orderId, OrderStatus status,
                            BigDecimal filledQuantity, BigDecimal remainingQuantity) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id().value(), order.status(),
                order.filledQuantity().value(), order.remainingQuantity().value());
    }
}

