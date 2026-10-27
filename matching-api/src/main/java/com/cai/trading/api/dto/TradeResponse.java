package com.cai.trading.api.dto;

import com.cai.trading.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(UUID tradeId, String symbol, UUID makerOrderId, UUID takerOrderId,
                            BigDecimal price, BigDecimal quantity, Instant executedAt) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(trade.id().value(), trade.symbol().value(),
                trade.makerOrderId().value(), trade.takerOrderId().value(),
                trade.executionPrice().value(), trade.executionQuantity().value(), trade.executedAt());
    }
}

