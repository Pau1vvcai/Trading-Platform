package com.cai.trading.application;

import com.cai.trading.domain.AccountId;

import java.util.function.Supplier;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 供单元测试和单实例部署使用的并发安全幂等存储。 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentMap<RequestKey, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public <T> T executeOnce(AccountId accountId, String key, String requestDigest,
                             Class<T> resultType, Supplier<T> action) {
        RequestKey requestKey = new RequestKey(
                Objects.requireNonNull(accountId), requireText(key), requireText(requestDigest));
        Entry candidate = new Entry(requestDigest, resultType);
        Entry existing = entries.putIfAbsent(requestKey.withoutDigest(), candidate);
        Entry selected = existing == null ? candidate : existing;

        if (!selected.digest.equals(requestDigest) || !selected.resultType.equals(resultType)) {
            throw new com.cai.trading.domain.DomainException(
                    com.cai.trading.domain.ErrorCode.DUPLICATE_REQUEST_CONFLICT,
                    "Idempotency key was already used for a different request");
        }

        if (existing == null) {
            try {
                T result = Objects.requireNonNull(action.get(), "idempotent action result");
                candidate.result.complete(result);
                return result;
            } catch (Throwable error) {
                entries.remove(requestKey.withoutDigest(), candidate);
                candidate.result.completeExceptionally(error);
                throw propagate(error);
            }
        }

        try {
            return resultType.cast(selected.result.join());
        } catch (CompletionException error) {
            throw propagate(error.getCause());
        }
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new com.cai.trading.domain.DomainException(
                    com.cai.trading.domain.ErrorCode.INVALID_ORDER, "Idempotency value cannot be blank");
        }
        return value;
    }

    private static RuntimeException propagate(Throwable error) {
        return error instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(error);
    }

    private record RequestKey(AccountId accountId, String key, String digest) {
        RequestKey withoutDigest() {
            return new RequestKey(accountId, key, "");
        }
    }

    private static final class Entry {
        private final String digest;
        private final Class<?> resultType;
        private final CompletableFuture<Object> result = new CompletableFuture<>();

        private Entry(String digest, Class<?> resultType) {
            this.digest = digest;
            this.resultType = resultType;
        }
    }
}

