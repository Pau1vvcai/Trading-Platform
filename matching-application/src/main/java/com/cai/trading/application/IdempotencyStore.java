package com.cai.trading.application;

import com.cai.trading.domain.AccountId;

import java.util.function.Supplier;

/**
 * 幂等边界：相同账户+键+摘要返回原结果；相同键不同摘要报冲突。
 * 实现必须保证并发调用中 action 至多执行一次。
 */
public interface IdempotencyStore {
    <T> T executeOnce(AccountId accountId, String key, String requestDigest,
                      Class<T> resultType, Supplier<T> action);
}

