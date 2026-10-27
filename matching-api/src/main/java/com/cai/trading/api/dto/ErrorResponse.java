package com.cai.trading.api.dto;

import com.cai.trading.domain.ErrorCode;

public record ErrorResponse(ErrorCode code, String message, String correlationId) {
}

