package com.cai.trading.api.dto;

import com.cai.trading.analytics.ResearchResult;
import com.cai.trading.analytics.WalkForwardMetric;
import com.cai.trading.domain.Symbol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Python 研究服务的回写契约，保留数据版本和每个样本外窗口。 */
public record ResearchResultRequest(
        @NotNull UUID experimentId,
        @NotBlank String datasetVersion,
        @NotBlank String symbol,
        @NotBlank String target,
        @NotBlank String model,
        @Positive int sampleCount,
        @Positive int foldCount,
        @NotNull @PositiveOrZero BigDecimal baselineMae,
        @NotNull @PositiveOrZero BigDecimal modelMae,
        @NotNull BigDecimal improvementPct,
        @NotNull Instant createdAt,
        @NotBlank String leakagePolicy,
        @NotBlank String costAssumption,
        @NotEmpty List<@Valid FoldRequest> folds) {

    public ResearchResult toDomain() {
        return new ResearchResult(experimentId, datasetVersion, new Symbol(symbol), target, model,
                sampleCount, foldCount, baselineMae, modelMae, improvementPct, createdAt,
                leakagePolicy, costAssumption, folds.stream().map(FoldRequest::toDomain).toList());
    }

    public record FoldRequest(
            @Positive int fold,
            @NotNull Instant trainFrom,
            @NotNull Instant trainTo,
            @NotNull Instant testFrom,
            @NotNull Instant testTo,
            @Positive int trainSamples,
            @Positive int testSamples,
            @NotNull @PositiveOrZero BigDecimal baselineMae,
            @NotNull @PositiveOrZero BigDecimal modelMae) {
        WalkForwardMetric toDomain() {
            return new WalkForwardMetric(fold, trainFrom, trainTo, testFrom, testTo,
                    trainSamples, testSamples, baselineMae, modelMae);
        }
    }
}
