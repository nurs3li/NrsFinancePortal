package com.nurseli.whaleanalytics.domain;

public record PatternResult(
        PatternType dominantPattern,
        int confidenceScore  // 0–100
) {
    public boolean isManipulative() {
        return dominantPattern == PatternType.PUMP_AND_DUMP
                || dominantPattern == PatternType.HIGH_FREQUENCY;
    }
}
