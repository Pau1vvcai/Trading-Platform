package com.cai.trading.analytics;

import com.cai.trading.domain.Symbol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 测试和单机演示用仓库；生产环境由 PostgreSQL 适配器替代。 */
public final class InMemoryMarketDataRepository implements MarketDataRepository {
    private final List<MarketBar> bars = new CopyOnWriteArrayList<>();

    @Override
    public void saveAll(List<MarketBar> values) {
        bars.addAll(List.copyOf(values));
    }

    @Override
    public List<MarketBar> find(Symbol symbol, Instant fromInclusive, Instant toExclusive) {
        return bars.stream().filter(bar -> bar.symbol().equals(symbol))
                .filter(bar -> !bar.timestamp().isBefore(fromInclusive))
                .filter(bar -> bar.timestamp().isBefore(toExclusive))
                .sorted(Comparator.comparing(MarketBar::timestamp)).toList();
    }

    @Override
    public long count(Symbol symbol) {
        return bars.stream().filter(bar -> bar.symbol().equals(symbol)).count();
    }
}
