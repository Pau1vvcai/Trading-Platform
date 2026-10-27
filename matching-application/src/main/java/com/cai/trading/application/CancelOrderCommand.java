package com.cai.trading.application;

import com.cai.trading.domain.AccountId;
import com.cai.trading.domain.OrderId;
import com.cai.trading.domain.Symbol;

public record CancelOrderCommand(AccountId accountId, OrderId orderId, Symbol symbol) {
}

