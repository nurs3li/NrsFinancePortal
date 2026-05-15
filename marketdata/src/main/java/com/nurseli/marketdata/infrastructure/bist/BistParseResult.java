package com.nurseli.marketdata.infrastructure.bist;

import java.util.List;

/**
 * HisseTekil (veya benzeri) JSON satırlarının parse çıktısı.
 */
public record BistParseResult(
        List<BistEquityDailyPrice> rows,
        int skippedCount,
        List<String> warnings
) {
    public BistParseResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static BistParseResult empty() {
        return new BistParseResult(List.of(), 0, List.of());
    }

    public static BistParseResult success(List<BistEquityDailyPrice> rows, int skippedCount, List<String> warnings) {
        return new BistParseResult(rows, skippedCount, warnings == null ? List.of() : warnings);
    }

    public static BistParseResult partial(List<BistEquityDailyPrice> rows, int skippedCount, List<String> warnings) {
        return new BistParseResult(rows, skippedCount, warnings == null ? List.of() : warnings);
    }
}
