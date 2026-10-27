package com.cai.trading.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(
        @NotNull UUID userId,
        @NotBlank String currency,
        @NotNull @DecimalMin("0.0") BigDecimal initialCash
) {
}

