package com.cai.trading.infrastructure;

import com.cai.trading.application.IdempotencyStore;
import com.cai.trading.domain.AccountId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.Supplier;

/**
 * 单实例 Redis 加速适配器。权威幂等记录由 PostgreSQL 实现；此类只适用于本地缓存场景。
 */
public final class RedisIdempotencyStore implements IdempotencyStore {
    private final StringRedisTemplate redis;
    private final com.cai.trading.application.InMemoryIdempotencyStore local =
            new com.cai.trading.application.InMemoryIdempotencyStore();

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = java.util.Objects.requireNonNull(redis);
    }

    @Override
    public <T> T executeOnce(AccountId accountId, String key, String requestDigest,
                             Class<T> resultType, Supplier<T> action) {
        // Redis 在 v0.1 仅记录命中信息，不承担权威 exactly-once 语义。
        T result = local.executeOnce(accountId, key, requestDigest, resultType, action);
        redis.opsForValue().set("idempotency-cache:" + accountId.value() + ":" + key,
                requestDigest, java.time.Duration.ofHours(24));
        return result;
    }
}

