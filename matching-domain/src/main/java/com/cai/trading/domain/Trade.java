package com.cai.trading.domain;

import java.time.Instant;
import java.util.Objects;

/** 不可变成交记录；executionPrice 使用 maker 订单价格。 */
public record Trade(
        TradeId id,
        Symbol symbol,
        OrderId makerOrderId,
        OrderId takerOrderId,
        AccountId buyerAccountId,
        AccountId sellerAccountId,
        Price executionPrice,
        Quantity executionQuantity,
        Instant executedAt
) {
    public Trade {
        Objects.requireNonNull(id, "trade id");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(makerOrderId, "maker order id");
        Objects.requireNonNull(takerOrderId, "taker order id");
        Objects.requireNonNull(buyerAccountId, "buyer account id");
        Objects.requireNonNull(sellerAccountId, "seller account id");
        Objects.requireNonNull(executionPrice, "execution price");
        Objects.requireNonNull(executionQuantity, "execution quantity");
        Objects.requireNonNull(executedAt, "execution time");
        if (executionQuantity.isZero()) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Execution quantity must be positive");
        }
    }
}

