package com.cai.trading.analytics;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;

import static com.cai.trading.analytics.AnalyticsFixtures.SYMBOL;
import static org.junit.jupiter.api.Assertions.*;

class CsvMarketDataImporterTest {
    @Test
    void should_import_in_batches_and_keep_valid_rows() throws Exception {
        InMemoryMarketDataRepository repository = new InMemoryMarketDataRepository();
        String csv = "timestamp,symbol,open,high,low,close,volume\n"
                + "2026-01-01T00:01:00Z,BTC/USD,10,11,9,10,2\n"
                + "broken,row\n"
                + "2026-01-01T00:00:00Z,BTC/USD,9,10,8,9,3\n";
        CsvImportResult result = new CsvMarketDataImporter(repository, 1).importCsv(new StringReader(csv));
        assertAll(
                () -> assertEquals(2, result.importedRows()),
                () -> assertEquals(1, result.errors().size()),
                () -> assertEquals(2, repository.count(SYMBOL)),
                () -> assertEquals(Instant.parse("2026-01-01T00:00:00Z"),
                        repository.find(SYMBOL, Instant.EPOCH, Instant.MAX).getFirst().timestamp()));
    }

    @Test
    void should_reject_unknown_header() {
        CsvMarketDataImporter importer = new CsvMarketDataImporter(new InMemoryMarketDataRepository(), 100);
        assertThrows(IllegalArgumentException.class,
                () -> importer.importCsv(new StringReader("time,price\n1,2")));
    }

    @Test
    void should_make_error_list_immutable() {
        CsvImportResult result = new CsvImportResult(0, java.util.List.of("bad"));
        assertThrows(UnsupportedOperationException.class, () -> result.errors().add("other"));
    }
}
