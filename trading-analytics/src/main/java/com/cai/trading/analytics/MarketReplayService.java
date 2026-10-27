package com.cai.trading.analytics;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 按历史时间顺序同步回放；调度速度由 API/任务层决定。 */
public final class MarketReplayService {
    public long replay(List<MarketBar> bars, Consumer<MarketBar> sink) {
        Objects.requireNonNull(sink);
        List<MarketBar> ordered = bars.stream()
                .sorted(Comparator.comparing(MarketBar::timestamp)).toList();
        ordered.forEach(sink);
        return ordered.size();
    }
}
