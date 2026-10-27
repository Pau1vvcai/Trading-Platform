package com.cai.trading.analytics;

import com.cai.trading.domain.Symbol;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResearchResultTest {
    @Test
    void should_accept_non_overlapping_walk_forward_folds_and_copy_the_list() {
        var folds = new java.util.ArrayList<>(List.of(fold()));
        ResearchResult result = result(folds);
        folds.clear();

        assertAll(
                () -> assertEquals(1, result.folds().size()),
                () -> assertEquals(new Symbol("BTC/USD"), result.symbol()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> result.folds().add(fold())));
    }

    @Test
    void should_reject_overlapping_training_and_test_windows() {
        assertThrows(IllegalArgumentException.class, () -> new WalkForwardMetric(1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"),
                40, 20, new BigDecimal("0.02"), new BigDecimal("0.01")));
    }

    @Test
    void should_require_fold_count_to_match_fold_metrics() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchResult(UUID.randomUUID(),
                "dataset-v1", new Symbol("BTC-USD"), "next_abs_return", "RIDGE", 60, 2,
                new BigDecimal("0.02"), new BigDecimal("0.01"), new BigDecimal("50"),
                Instant.parse("2026-04-01T00:00:00Z"), "past-only features", "not applicable",
                List.of(fold())));
    }

    private static ResearchResult result(List<WalkForwardMetric> folds) {
        return new ResearchResult(UUID.randomUUID(), "dataset-v1", new Symbol("BTC-USD"),
                "next_abs_return", "RIDGE", 60, 1, new BigDecimal("0.02"),
                new BigDecimal("0.01"), new BigDecimal("50"),
                Instant.parse("2026-04-01T00:00:00Z"), "past-only features",
                "prediction experiment; no trading costs", folds);
    }

    private static WalkForwardMetric fold() {
        return new WalkForwardMetric(1, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:00:00Z"), 40, 20,
                new BigDecimal("0.02"), new BigDecimal("0.01"));
    }
}
