package com.cai.trading.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.cai.trading.analytics.AnalyticsFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class RealtimeAndAsyncTest {
    @Test
    void should_sequence_events_and_limit_history() {
        SequencedEventHub hub = new SequencedEventHub(2, Clock.systemUTC());
        List<RealtimeEvent> received = new ArrayList<>();
        hub.subscribe("BTC/USD", received::add);
        hub.publish("BTC/USD", "TRADE", Map.of("price", 10));
        hub.publish("BTC/USD", "TRADE", Map.of("price", 11));
        hub.publish("BTC/USD", "TRADE", Map.of("price", 12));
        assertAll(
                () -> assertEquals(List.of(1L, 2L, 3L), received.stream().map(RealtimeEvent::sequence).toList()),
                () -> assertEquals(List.of(2L, 3L), hub.eventsAfter("BTC/USD", 0).stream()
                        .map(RealtimeEvent::sequence).toList()));
    }

    @Test
    void should_unsubscribe_listener() throws Exception {
        SequencedEventHub hub = new SequencedEventHub(2, Clock.systemUTC());
        List<RealtimeEvent> received = new ArrayList<>();
        AutoCloseable subscription = hub.subscribe("BTC/USD", received::add);
        subscription.close();
        hub.publish("BTC/USD", "TRADE", Map.of());
        assertTrue(received.isEmpty());
    }

    @Test
    void should_complete_backtest_asynchronously() throws Exception {
        InMemoryBacktestJobRepository jobs = new InMemoryBacktestJobRepository();
        List<BacktestJobStatus> statuses = new CopyOnWriteArrayList<>();
        try (AsyncBacktestService service = new AsyncBacktestService(new BacktestEngine(), jobs,
                1, Clock.systemUTC(), job -> statuses.add(job.status()))) {
            var id = service.submit(SYMBOL, bars("10", "11", "12"),
                    new DollarCostAveragingStrategy(1, new BigDecimal("0.25")),
                    new BigDecimal("100"), BigDecimal.ZERO);
            BacktestJob job = awaitTerminal(service, id);
            assertAll(
                    () -> assertEquals(BacktestJobStatus.SUCCEEDED, job.status()),
                    () -> assertEquals(100, job.progress()),
                    () -> assertTrue(job.resultOptional().isPresent()),
                    () -> assertTrue(statuses.containsAll(List.of(BacktestJobStatus.QUEUED,
                            BacktestJobStatus.RUNNING, BacktestJobStatus.SUCCEEDED))));
        }
    }

    @Test
    void should_capture_failed_backtest() throws Exception {
        try (AsyncBacktestService service = new AsyncBacktestService(new BacktestEngine(),
                new InMemoryBacktestJobRepository(), 1, Clock.systemUTC(), BacktestJobListener.noop())) {
            var id = service.submit(SYMBOL, List.of(), new DollarCostAveragingStrategy(1, BigDecimal.ONE),
                    BigDecimal.TEN, BigDecimal.ZERO);
            assertEquals(BacktestJobStatus.FAILED, awaitTerminal(service, id).status());
        }
    }

    private static BacktestJob awaitTerminal(AsyncBacktestService service, java.util.UUID id) throws Exception {
        for (int i = 0; i < 100; i++) {
            BacktestJob job = service.get(id);
            if (job.status() == BacktestJobStatus.SUCCEEDED || job.status() == BacktestJobStatus.FAILED) return job;
            Thread.sleep(5);
        }
        throw new AssertionError("job did not finish");
    }
}
