package com.cai.trading.analytics;

import com.cai.trading.domain.Symbol;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 研究结果端口；Python 服务只依赖 HTTP 契约，具体持久化由平台适配。 */
public interface ResearchResultRepository {
    void save(ResearchResult result);
    Optional<ResearchResult> findById(UUID experimentId);
    List<ResearchResult> findLatest(Symbol symbol, int limit);
}
