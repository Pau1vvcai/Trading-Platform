package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPoint(Instant timestamp, BigDecimal equity, BigDecimal drawdown) { }
