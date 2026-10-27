package com.cai.trading.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PositionCreditRequest(
        @NotBlank String symbol,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) { }

