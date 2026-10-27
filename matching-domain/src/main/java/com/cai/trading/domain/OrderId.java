package com.cai.trading.domain;

import java.util.Objects;
import java.util.UUID;

/** 订单实体标识；不要与幂等键混用。 */
public record OrderId(UUID value) {
    public OrderId {
        Objects.requireNonNull(value, "order id");
    }
}

