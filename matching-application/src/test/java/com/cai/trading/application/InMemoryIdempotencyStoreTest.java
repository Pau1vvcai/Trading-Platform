package com.cai.trading.application;

import com.cai.trading.domain.AccountId;
import com.cai.trading.domain.DomainException;
import com.cai.trading.domain.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cai.trading.application.ApplicationTestFixtures.accountId;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryIdempotencyStoreTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void should_return_original_result_for_sequential_retry() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        AtomicInteger calls = new AtomicInteger();
        AccountId accountId = accountId("buyer");

        String first = store.executeOnce(accountId, "key", "digest", String.class,
                () -> "result-" + calls.incrementAndGet());
        String retry = store.executeOnce(accountId, "key", "digest", String.class,
                () -> "result-" + calls.incrementAndGet());

        assertEquals("result-1", first);
        assertEquals(first, retry);
        assertEquals(1, calls.get());
    }

    @Test
    void should_reject_same_key_with_different_request_digest() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        AccountId accountId = accountId("buyer");
        store.executeOnce(accountId, "key", "digest-a", String.class, () -> "ok");

        DomainException error = assertThrows(DomainException.class,
                () -> store.executeOnce(accountId, "key", "digest-b", String.class, () -> "wrong"));

        assertEquals(ErrorCode.DUPLICATE_REQUEST_CONFLICT, error.code());
    }

    @Test
    void should_scope_same_key_by_account() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        String first = store.executeOnce(accountId("a"), "key", "digest", String.class, () -> "a");
        String second = store.executeOnce(accountId("b"), "key", "digest", String.class, () -> "b");

        assertEquals("a", first);
        assertEquals("b", second);
    }

    @Test
    void should_not_cache_internal_failure_so_retry_can_execute() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        AccountId accountId = accountId("buyer");
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> store.executeOnce(
                accountId, "key", "digest", String.class, () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("temporary failure");
                }));

        String retry = store.executeOnce(accountId, "key", "digest", String.class, () -> {
            calls.incrementAndGet();
            return "recovered";
        });

        assertEquals("recovered", retry);
        assertEquals(2, calls.get());
    }

    @Test
    void should_execute_action_once_for_concurrent_retries() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        AccountId accountId = accountId("buyer");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        CountDownLatch callersReady = new CountDownLatch(8);
        CountDownLatch startCallers = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            futures.add(executor.submit(() -> {
                callersReady.countDown();
                assertTrue(startCallers.await(1, TimeUnit.SECONDS));
                return store.executeOnce(accountId, "key", "digest", String.class, () -> {
                    calls.incrementAndGet();
                    actionStarted.countDown();
                    try {
                        assertTrue(releaseAction.await(1, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    return "one-result";
                });
            }));
        }

        assertTrue(callersReady.await(1, TimeUnit.SECONDS));
        startCallers.countDown();
        assertTrue(actionStarted.await(1, TimeUnit.SECONDS));
        releaseAction.countDown();

        for (Future<String> future : futures) {
            assertEquals("one-result", future.get(2, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
    }
}

