package com.cai.trading.analytics;

import com.cai.trading.domain.Quantity;
import com.cai.trading.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 单根 OHLCV 行情；价格必须为正且高低价必须包住开盘价与收盘价。 */
public record MarketBar(Symbol symbol, Instant timestamp,
                        BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                        Quantity volume) {
    public MarketBar {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timestamp, "timestamp");
        open = normalizePositive(open, "open");
        high = normalizePositive(high, "high");
        low = normalizePositive(low, "low");
        close = normalizePositive(close, "close");
        Objects.requireNonNull(volume, "volume");
        if (volume.isZero()) throw new IllegalArgumentException("volume must be positive");
        if (high.compareTo(low) < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("Invalid OHLC price range");
        }
    }

    private static BigDecimal normalizePositive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value.stripTrailingZeros();
    }
}
