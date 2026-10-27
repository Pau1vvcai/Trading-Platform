package com.cai.trading.domain;

import java.util.Objects;
import java.util.UUID;

/** 模拟交易账户标识。 */
public record AccountId(UUID value) {
    public AccountId{
        Objects.requireNonNull(value, "account id");
    }

}

