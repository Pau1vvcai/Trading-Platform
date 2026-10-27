package com.cai.trading.application;

import com.cai.trading.domain.Account;
import com.cai.trading.domain.Order;
import com.cai.trading.domain.Trade;

public interface SettlementService {
    void freezeFor(Order order, Account account, PlaceOrderCommand command);
    void settle(Trade trade, Order maker, Order taker, Account makerAccount, Account takerAccount);
    void releaseAfterCancel(Order order, Account account);
}

