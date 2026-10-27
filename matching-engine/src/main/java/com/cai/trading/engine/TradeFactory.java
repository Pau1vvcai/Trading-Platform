package com.cai.trading.engine;

import com.cai.trading.domain.Order;
import com.cai.trading.domain.Price;
import com.cai.trading.domain.Quantity;
import com.cai.trading.domain.Trade;

/** 把 ID 与时间生成移出纯撮合循环，便于测试复现。 */
@FunctionalInterface
public interface TradeFactory {
    Trade create(Order maker, Order taker, Price price, Quantity quantity);
}

