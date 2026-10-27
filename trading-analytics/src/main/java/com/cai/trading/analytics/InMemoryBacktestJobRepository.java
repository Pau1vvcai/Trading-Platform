package com.cai.trading.analytics;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryBacktestJobRepository implements BacktestJobRepository {
    private final ConcurrentMap<UUID, BacktestJob> jobs = new ConcurrentHashMap<>();
    @Override public void save(BacktestJob job) { jobs.put(job.id(), job); }
    @Override public Optional<BacktestJob> findById(UUID id) { return Optional.ofNullable(jobs.get(id)); }
}
