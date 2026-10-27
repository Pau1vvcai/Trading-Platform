package com.cai.trading.analytics;

import com.cai.trading.domain.Quantity;
import com.cai.trading.domain.Symbol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 流式读取 timestamp,symbol,open,high,low,close,volume CSV。
 * 每批写入仓库，能够处理十万行以上数据而不把整个文件常驻内存。
 */
public final class CsvMarketDataImporter {
    private final MarketDataRepository repository;
    private final int batchSize;

    public CsvMarketDataImporter(MarketDataRepository repository, int batchSize) {
        this.repository = Objects.requireNonNull(repository);
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        this.batchSize = batchSize;
    }

    public CsvImportResult importCsv(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        String header = reader.readLine();
        if (header == null || !normalizeHeader(header).equals(
                "timestamp,symbol,open,high,low,close,volume")) {
            throw new IllegalArgumentException("Unexpected CSV header");
        }

        List<MarketBar> batch = new ArrayList<>(batchSize);
        List<String> errors = new ArrayList<>();
        long imported = 0;
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;
            try {
                batch.add(parse(line));
                if (batch.size() == batchSize) {
                    repository.saveAll(batch);
                    imported += batch.size();
                    batch = new ArrayList<>(batchSize);
                }
            } catch (RuntimeException error) {
                errors.add("line " + lineNumber + ": " + error.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            repository.saveAll(batch);
            imported += batch.size();
        }
        return new CsvImportResult(imported, errors);
    }

    private static MarketBar parse(String line) {
        String[] values = line.split(",", -1);
        if (values.length != 7) throw new IllegalArgumentException("expected 7 columns");
        return new MarketBar(new Symbol(values[1].trim()), Instant.parse(values[0].trim()),
                decimal(values[2]), decimal(values[3]), decimal(values[4]), decimal(values[5]),
                new Quantity(decimal(values[6])));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.trim());
    }

    private static String normalizeHeader(String header) {
        return header.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
