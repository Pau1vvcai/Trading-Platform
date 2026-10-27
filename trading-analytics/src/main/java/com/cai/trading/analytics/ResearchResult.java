package com.cai.trading.analytics;

import com.cai.trading.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Python 研究服务回写的、可追溯且只包含样本外指标的实验结果。 */
public record ResearchResult(UUID experimentId, String datasetVersion, Symbol symbol,
                             String target, String model, int sampleCount, int foldCount,
                             BigDecimal baselineMae, BigDecimal modelMae,
                             BigDecimal improvementPct, Instant createdAt,
                             String leakagePolicy, String costAssumption,
                             List<WalkForwardMetric> folds) {
    public ResearchResult {
        Objects.requireNonNull(experimentId, "experimentId");
        datasetVersion = required(datasetVersion, "datasetVersion");
        Objects.requireNonNull(symbol, "symbol");
        target = required(target, "target");
        model = required(model, "model");
        Objects.requireNonNull(baselineMae, "baselineMae");
        Objects.requireNonNull(modelMae, "modelMae");
        Objects.requireNonNull(improvementPct, "improvementPct");
        Objects.requireNonNull(createdAt, "createdAt");
        leakagePolicy = required(leakagePolicy, "leakagePolicy");
        costAssumption = required(costAssumption, "costAssumption");
        folds = List.copyOf(Objects.requireNonNull(folds, "folds"));
        if (sampleCount <= 0 || foldCount <= 0 || foldCount != folds.size()) {
            throw new IllegalArgumentException("invalid sample or fold count");
        }
        if (baselineMae.signum() < 0 || modelMae.signum() < 0) {
            throw new IllegalArgumentException("MAE cannot be negative");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
