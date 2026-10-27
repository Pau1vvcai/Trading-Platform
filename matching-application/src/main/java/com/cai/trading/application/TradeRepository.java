package com.cai.trading.application;

import com.cai.trading.domain.Symbol;
import com.cai.trading.domain.Trade;

import java.util.List;

public interface TradeRepository {
    void saveAll(List<Trade> trades);
    List<Trade> findBySymbol(Symbol symbol);
}

