package com.cai.trading.analytics;

import java.util.List;

public interface TradingStrategy {
    String name();
    StrategyDecision decide(int index, List<MarketBar> bars);
}
