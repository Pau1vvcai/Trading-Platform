package com.cai.trading.application;

import com.cai.trading.domain.*;

import java.util.Optional;

/** 下单意图；市价买单通过 maxSpend 明确最大冻结金额。 */
public record PlaceOrderCommand(
        String idempotencyKey,
        AccountId accountId,
        Symbol symbol,
        Side side,
        OrderType type,
        Optional<Price> limitPrice,
        Quantity quantity,
        Optional<Money> maxSpend
) {
}

