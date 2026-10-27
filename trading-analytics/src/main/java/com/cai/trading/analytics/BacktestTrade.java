package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.time.Instant;

public record BacktestTrade(Instant timestamp, StrategyAction action, BigDecimal price,
                            BigDecimal quantity, BigDecimal fee, BigDecimal cashAfter) { }
