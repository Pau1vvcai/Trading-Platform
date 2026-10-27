package com.cai.trading.api;

import com.cai.trading.analytics.*;
import com.cai.trading.api.dto.BacktestRequest;
import com.cai.trading.domain.Symbol;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {
    private final MarketDataRepository marketData;
    private final CsvMarketDataImporter importer;
    private final MarketReplayService replay;
    private final AsyncBacktestService backtests;
    private final SequencedEventHub events;
    private final Counter importedBars;
    private final Counter submittedBacktests;

    public AnalyticsController(MarketDataRepository marketData, CsvMarketDataImporter importer,
                               MarketReplayService replay, AsyncBacktestService backtests,
                               SequencedEventHub events, MeterRegistry metrics) {
        this.marketData = marketData; this.importer = importer; this.replay = replay;
        this.backtests = backtests; this.events = events;
        this.importedBars = metrics.counter("trading.market_data.imported_bars");
        this.submittedBacktests = metrics.counter("trading.backtest.submitted");
    }

    @PostMapping(value = "/market-data/import", consumes = "multipart/form-data")
    public CsvImportResult importCsv(@RequestPart("file") MultipartFile file) throws IOException {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CsvImportResult result = importer.importCsv(reader);
            importedBars.increment(result.importedRows());
            return result;
        }
    }

    @GetMapping("/market-data/bars")
    public List<MarketBar> bars(@RequestParam("symbol") String symbol,
                                @RequestParam("from") Instant from,
                                @RequestParam("to") Instant to) {
        return marketData.find(new Symbol(symbol), from, to);
    }

    @PostMapping("/market-data/replay")
    public Map<String, Long> replay(@RequestParam("symbol") String symbol,
                                    @RequestParam("from") Instant from,
                                    @RequestParam("to") Instant to) {
        Symbol key = new Symbol(symbol);
        long count = replay.replay(marketData.find(key, from, to), bar -> events.publish(
                key.value(), "MARKET_BAR", Map.of("timestamp", bar.timestamp().toString(),
                        "open", bar.open(), "high", bar.high(), "low", bar.low(),
                        "close", bar.close(), "volume", bar.volume().value())));
        return Map.of("replayed", count);
    }

    @PostMapping("/backtests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> submit(@Valid @RequestBody BacktestRequest request) {
        Symbol symbol = new Symbol(request.symbol());
        TradingStrategy strategy = strategy(request);
        UUID id = backtests.submit(symbol, marketData.find(symbol, request.from(), request.to()),
                strategy, request.initialCapital(), request.feeRate());
        submittedBacktests.increment();
        return Map.of("jobId", id);
    }

    @GetMapping("/backtests/{id}")
    public BacktestJob get(@PathVariable("id") UUID id) { return backtests.get(id); }

    private static TradingStrategy strategy(BacktestRequest request) {
        return switch (request.strategy().trim().toUpperCase()) {
            case "SMA" -> new SmaCrossStrategy(required(request.shortWindow(), "shortWindow"),
                    required(request.longWindow(), "longWindow"));
            case "BREAKOUT" -> new BreakoutStrategy(required(request.lookback(), "lookback"));
            case "DCA" -> new DollarCostAveragingStrategy(required(request.interval(), "interval"),
                    request.allocation() == null ? java.math.BigDecimal.ONE : request.allocation());
            default -> throw new IllegalArgumentException("Unknown strategy: " + request.strategy());
        };
    }

    private static int required(Integer value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
