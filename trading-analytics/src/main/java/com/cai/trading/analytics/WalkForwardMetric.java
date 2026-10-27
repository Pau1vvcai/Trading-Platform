package com.cai.trading.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 单个扩展窗口的样本外指标；时间边界便于审计训练集与测试集是否重叠。 */
public record WalkForwardMetric(int fold, Instant trainFrom, Instant trainTo,
                                Instant testFrom, Instant testTo,
                                int trainSamples, int testSamples,
                                BigDecimal baselineMae, BigDecimal modelMae) {
    public WalkForwardMetric {
        if (fold <= 0) throw new IllegalArgumentException("fold must be positive");
        Objects.requireNonNull(trainFrom, "trainFrom");
        Objects.requireNonNull(trainTo, "trainTo");
        Objects.requireNonNull(testFrom, "testFrom");
        Objects.requireNonNull(testTo, "testTo");
        Objects.requireNonNull(baselineMae, "baselineMae");
        Objects.requireNonNull(modelMae, "modelMae");
        if (trainSamples <= 0 || testSamples <= 0) {
            throw new IllegalArgumentException("sample counts must be positive");
        }
        if (!trainTo.isBefore(testFrom)) {
            throw new IllegalArgumentException("training data must end before test data starts");
        }
        if (baselineMae.signum() < 0 || modelMae.signum() < 0) {
            throw new IllegalArgumentException("MAE cannot be negative");
        }
    }
}
