package com.cai.trading.application;

import com.cai.trading.domain.Order;
import com.cai.trading.domain.OrderId;
import com.cai.trading.engine.OrderBookSnapshot;

/**
 * 下单/撤单用例编排入口。
 * 实现按“读取账户→风控→冻结→保存→撮合→结算→持久化”组织完整流程。
 */
public interface OrderApplicationService {
    PlaceOrderResult place(PlaceOrderCommand command);
    Order cancel(CancelOrderCommand command);
    Order get(OrderId orderId);
    OrderBookSnapshot getOrderBook(com.cai.trading.domain.Symbol symbol);
}

