package com.cai.trading.engine;

import com.cai.trading.domain.Order;
import com.cai.trading.domain.Symbol;

import java.util.List;

/** API 查询和恢复测试使用的只读快照。 */
public record OrderBookSnapshot(Symbol symbol, List<Order> bids, List<Order> asks) {
    public OrderBookSnapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}

