package com.cai.trading.engine;

import com.cai.trading.domain.Trade;

import java.util.List;

/** 撮合输出；实现时必须对列表做不可变拷贝。 */
public record MatchResult(List<Trade> trades, List<OrderUpdate> orderUpdates) {
    public MatchResult {
        trades = List.copyOf(trades);
        orderUpdates = List.copyOf(orderUpdates);
    }
}

