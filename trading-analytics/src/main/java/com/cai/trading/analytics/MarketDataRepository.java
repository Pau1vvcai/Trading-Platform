package com.cai.trading.analytics;

import com.cai.trading.domain.Symbol;

import java.time.Instant;
import java.util.List;

public interface MarketDataRepository {
    void saveAll(List<MarketBar> bars);
    List<MarketBar> find(Symbol symbol, Instant fromInclusive, Instant toExclusive);
    long count(Symbol symbol);
}
