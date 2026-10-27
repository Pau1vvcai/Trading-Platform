package com.cai.trading.domain;

import java.util.Objects;
import java.util.UUID;

/** 单笔成交标识。 */
public record TradeId(UUID value) {
    public TradeId {
        Objects.requireNonNull(value, "trade id");
    }
}

