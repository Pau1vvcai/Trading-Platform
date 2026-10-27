package com.cai.trading.application;

import com.cai.trading.domain.Symbol;
import com.cai.trading.engine.MatchingEngine;

/** 每个 Symbol 对应一个撮合引擎实例。 */
public interface MatchingEngineRegistry {
    MatchingEngine engineFor(Symbol symbol);
}

