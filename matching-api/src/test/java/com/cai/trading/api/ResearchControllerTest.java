package com.cai.trading.api;

import com.cai.trading.analytics.ResearchResult;
import com.cai.trading.analytics.ResearchResultRepository;
import com.cai.trading.domain.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ResearchControllerTest {
    private StubRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        mvc = standaloneSetup(new ResearchController(repository))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void should_accept_auditable_research_result() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/research-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(id, "2026-02-01T00:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.experimentId").value(id.toString()))
                .andExpect(jsonPath("$.datasetVersion").value("sha256-abc"))
                .andExpect(jsonPath("$.folds[0].testSamples").value(20));

        assertEquals(id, repository.saved.experimentId());
        assertEquals(new Symbol("BTC/USD"), repository.saved.symbol());
    }

    @Test
    void should_reject_overlapping_walk_forward_windows() throws Exception {
        mvc.perform(post("/api/v1/research-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(UUID.randomUUID(), "2026-01-31T00:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"));
    }

    @Test
    void should_normalize_symbol_when_listing_results() throws Exception {
        mvc.perform(get("/api/v1/research-results").param("symbol", "btc-usd").param("limit", "5"))
                .andExpect(status().isOk());
        assertEquals(new Symbol("BTC/USD"), repository.requestedSymbol);
        assertEquals(5, repository.requestedLimit);
    }

    private static String payload(UUID id, String testFrom) {
        return """
                {
                  "experimentId":"%s", "datasetVersion":"sha256-abc", "symbol":"BTC-USD",
                  "target":"next_abs_return", "model":"RIDGE", "sampleCount":60,
                  "foldCount":1, "baselineMae":0.02, "modelMae":0.01,
                  "improvementPct":50, "createdAt":"2026-04-01T00:00:00Z",
                  "leakagePolicy":"features use information available at t only",
                  "costAssumption":"prediction-only; fees and slippage not applicable",
                  "folds":[{"fold":1,"trainFrom":"2026-01-01T00:00:00Z",
                    "trainTo":"2026-01-31T00:00:00Z","testFrom":"%s",
                    "testTo":"2026-03-01T00:00:00Z","trainSamples":40,
                    "testSamples":20,"baselineMae":0.02,"modelMae":0.01}]
                }
                """.formatted(id, testFrom);
    }

    private static final class StubRepository implements ResearchResultRepository {
        private final List<ResearchResult> values = new ArrayList<>();
        private ResearchResult saved;
        private Symbol requestedSymbol;
        private int requestedLimit;
        @Override public void save(ResearchResult result) { saved = result; values.add(result); }
        @Override public Optional<ResearchResult> findById(UUID id) {
            return values.stream().filter(value -> value.experimentId().equals(id)).findFirst();
        }
        @Override public List<ResearchResult> findLatest(Symbol symbol, int limit) {
            requestedSymbol = symbol; requestedLimit = limit; return List.copyOf(values);
        }
    }
}
