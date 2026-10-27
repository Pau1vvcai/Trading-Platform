package com.cai.trading.api;

import com.cai.trading.analytics.ResearchResult;
import com.cai.trading.analytics.ResearchResultRepository;
import com.cai.trading.api.dto.ResearchResultRequest;
import com.cai.trading.domain.Symbol;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** 接收 Python 模型实验结果，并向交易终端提供只读查询。 */
@RestController
@RequestMapping("/api/v1/research-results")
public class ResearchController {
    private final ResearchResultRepository results;

    public ResearchController(ResearchResultRepository results) { this.results = results; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResearchResult save(@Valid @RequestBody ResearchResultRequest request) {
        ResearchResult result = request.toDomain();
        results.save(result);
        return result;
    }

    @GetMapping("/{experimentId}")
    public ResearchResult get(@PathVariable("experimentId") UUID experimentId) {
        return results.findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Research experiment not found"));
    }

    @GetMapping
    public List<ResearchResult> latest(@RequestParam("symbol") String symbol,
                                       @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return results.findLatest(new Symbol(symbol), limit);
    }
}
