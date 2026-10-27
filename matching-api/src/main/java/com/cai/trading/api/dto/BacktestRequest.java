package com.cai.trading.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/** 不同策略只读取自己需要的参数，其余参数可以省略。 */
public record BacktestRequest(
        @NotBlank String symbol, @NotNull Instant from, @NotNull Instant to,
        @NotBlank String strategy, @Positive Integer shortWindow, @Positive Integer longWindow,
        @Positive Integer lookback, @Positive Integer interval,
        @DecimalMin("0.0000001") @DecimalMax("1") BigDecimal allocation,
        @NotNull @DecimalMin("0.01") BigDecimal initialCapital,
        @NotNull @DecimalMin("0") @DecimalMax("0.1") BigDecimal feeRate) { }
