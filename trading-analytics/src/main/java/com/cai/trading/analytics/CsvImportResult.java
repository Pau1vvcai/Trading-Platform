package com.cai.trading.analytics;

import java.util.List;

public record CsvImportResult(long importedRows, List<String> errors) {
    public CsvImportResult {
        errors = List.copyOf(errors);
    }
}
