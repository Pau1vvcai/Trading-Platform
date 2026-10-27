package com.cai.trading.infrastructure;

import com.cai.trading.analytics.ResearchResult;
import com.cai.trading.analytics.ResearchResultRepository;
import com.cai.trading.analytics.WalkForwardMetric;
import com.cai.trading.domain.Symbol;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.sql.Timestamp;

/** 将研究元数据和各 Walk-forward 窗口指标持久化到 PostgreSQL。 */
public final class PostgresResearchResultRepository implements ResearchResultRepository {
    private static final TypeReference<List<WalkForwardMetric>> FOLD_LIST = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresResearchResultRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(ResearchResult result) {
        jdbc.update("""
                INSERT INTO ml_research_results(
                  experiment_id, dataset_version, symbol, prediction_target, model_name,
                  sample_count, fold_count, baseline_mae, model_mae, improvement_pct,
                  created_at, leakage_policy, cost_assumption, fold_metrics)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT(experiment_id) DO UPDATE SET
                  dataset_version=EXCLUDED.dataset_version, symbol=EXCLUDED.symbol,
                  prediction_target=EXCLUDED.prediction_target, model_name=EXCLUDED.model_name,
                  sample_count=EXCLUDED.sample_count, fold_count=EXCLUDED.fold_count,
                  baseline_mae=EXCLUDED.baseline_mae, model_mae=EXCLUDED.model_mae,
                  improvement_pct=EXCLUDED.improvement_pct, created_at=EXCLUDED.created_at,
                  leakage_policy=EXCLUDED.leakage_policy, cost_assumption=EXCLUDED.cost_assumption,
                  fold_metrics=EXCLUDED.fold_metrics
                """, result.experimentId(), result.datasetVersion(), result.symbol().value(),
                result.target(), result.model(), result.sampleCount(), result.foldCount(),
                result.baselineMae(), result.modelMae(), result.improvementPct(), Timestamp.from(result.createdAt()),
                result.leakagePolicy(), result.costAssumption(), encode(result.folds()));
    }

    @Override
    public Optional<ResearchResult> findById(UUID experimentId) {
        return jdbc.query(SELECT + " WHERE experiment_id=?", this::map, experimentId)
                .stream().findFirst();
    }

    @Override
    public List<ResearchResult> findLatest(Symbol symbol, int limit) {
        if (limit <= 0 || limit > 100) throw new IllegalArgumentException("limit must be within 1..100");
        return jdbc.query(SELECT + " WHERE symbol=? ORDER BY created_at DESC LIMIT ?", this::map,
                symbol.value(), limit);
    }

    private ResearchResult map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ResearchResult(rs.getObject("experiment_id", UUID.class),
                rs.getString("dataset_version"), new Symbol(rs.getString("symbol")),
                rs.getString("prediction_target"), rs.getString("model_name"),
                rs.getInt("sample_count"), rs.getInt("fold_count"),
                rs.getBigDecimal("baseline_mae"), rs.getBigDecimal("model_mae"),
                rs.getBigDecimal("improvement_pct"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("leakage_policy"), rs.getString("cost_assumption"),
                decode(rs.getString("fold_metrics")));
    }

    private String encode(List<WalkForwardMetric> folds) {
        try { return mapper.writeValueAsString(folds); }
        catch (Exception error) { throw new IllegalStateException("Cannot encode fold metrics", error); }
    }

    private List<WalkForwardMetric> decode(String json) {
        try { return mapper.readValue(json, FOLD_LIST); }
        catch (Exception error) { throw new IllegalStateException("Cannot decode fold metrics", error); }
    }

    private static final String SELECT = """
            SELECT experiment_id, dataset_version, symbol, prediction_target, model_name,
                   sample_count, fold_count, baseline_mae, model_mae, improvement_pct,
                   created_at, leakage_policy, cost_assumption, fold_metrics::text AS fold_metrics
            FROM ml_research_results
            """;
}
