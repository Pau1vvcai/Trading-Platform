package com.cai.trading.application;

import java.util.function.Supplier;

/** 基础设施提供事务实现，应用服务据此声明完整用例的原子边界。 */
public interface TransactionRunner {
    <T> T inTransaction(Supplier<T> action);
}

