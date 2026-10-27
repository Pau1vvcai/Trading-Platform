package com.cai.trading.domain;

import java.util.Locale;
import java.util.Objects;

/** 交易标的代码。构造时规范化，避免同一标的产生多个订单簿。 */
public record Symbol(String value) {
    public Symbol {
        Objects.requireNonNull(value, "symbol");
        value = value.trim().toUpperCase(Locale.ROOT);
        // URL 和前端常用 BTC-USD；领域内部统一保存为 BTC/USD，避免形成两个订单簿。
        if (value.matches("[A-Z0-9._]+-[A-Z0-9._]+")) {
            value = value.replace('-', '/');
        }
        if (value.isEmpty() || value.length() > 20 || !value.matches("[A-Z0-9._-]+/[A-Z0-9._-]+")) {
            throw new DomainException(ErrorCode.INVALID_ORDER, "Invalid symbol: " + value);
        }
    }
}

