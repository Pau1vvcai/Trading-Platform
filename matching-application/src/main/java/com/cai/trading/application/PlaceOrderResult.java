package com.cai.trading.application;

import com.cai.trading.domain.OrderId;
import com.cai.trading.domain.OrderStatus;
import com.cai.trading.domain.Trade;

import java.util.List;

public record PlaceOrderResult(OrderId orderId, OrderStatus status, List<Trade> trades) {
}

