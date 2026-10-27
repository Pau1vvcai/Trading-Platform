package com.cai.trading.analytics;

import java.util.Optional;
import java.util.UUID;

public interface BacktestJobRepository {
    void save(BacktestJob job);
    Optional<BacktestJob> findById(UUID id);
}
