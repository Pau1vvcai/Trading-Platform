package com.cai.trading.engine;

import com.cai.trading.domain.OrderId;
import com.cai.trading.domain.OrderStatus;
import com.cai.trading.domain.Quantity;

/** 撮合后供应用层持久化的订单状态变化。 */
public record OrderUpdate(OrderId orderId, OrderStatus status, Quantity filled, Quantity remaining) {
}

