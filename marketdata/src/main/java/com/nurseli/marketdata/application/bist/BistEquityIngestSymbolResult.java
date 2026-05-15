package com.nurseli.marketdata.application.bist;

import java.util.List;

/**
 * Tek sembol için HisseTekil günlük ingest özeti.
 */
public record BistEquityIngestSymbolResult(
        String symbol,
        int rowsFetched,
        int rowsWritten,
        int rowsSkipped,
        boolean success,
        List<String> warnings) {

    public static BistEquityIngestSymbolResult empty(String symbol, List<String> warnings) {
        return new BistEquityIngestSymbolResult(symbol, 0, 0, 0, false, warnings == null ? List.of() : List.copyOf(warnings));
    }

    public static BistEquityIngestSymbolResult of(
            String symbol, int fetched, int written, int skipped, boolean success, List<String> warnings) {
        return new BistEquityIngestSymbolResult(
                symbol,
                fetched,
                written,
                skipped,
                success,
                warnings == null ? List.of() : List.copyOf(warnings));
    }
}
