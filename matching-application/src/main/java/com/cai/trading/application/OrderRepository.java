package com.cai.trading.application;

import com.cai.trading.domain.Order;
import com.cai.trading.domain.OrderId;
import com.cai.trading.domain.Symbol;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    List<Order> findActiveBySymbolInSequenceOrder(Symbol symbol);
    List<Symbol> findActiveSymbols();
    void save(Order order);
    void saveAll(List<Order> orders);
}

