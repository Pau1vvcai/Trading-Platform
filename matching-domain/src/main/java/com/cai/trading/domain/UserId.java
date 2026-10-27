package com.cai.trading.domain;

import java.util.Objects;
import java.util.UUID;

/** 用户实体的稳定标识。 */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "user id");
    }
}

