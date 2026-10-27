package com.cai.trading.api.dto;

import com.cai.trading.domain.OrderType;
import com.cai.trading.domain.Side;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** limitPrice 与 maxSpend 的条件校验留给 DTO 自定义校验或应用层。 */
public record PlaceOrderRequest(
        @NotNull UUID accountId,
        @NotBlank @Size(max = 20) String symbol,
        @NotNull Side side,
        @NotNull OrderType type,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal limitPrice,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal maxSpend
) {
}

