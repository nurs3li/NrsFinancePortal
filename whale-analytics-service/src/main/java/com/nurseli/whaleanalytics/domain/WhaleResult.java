package com.nurseli.whaleanalytics.domain;

import java.time.Instant;

public record WhaleResult(
        String userId,
        WhaleLevel level,
        WhaleMetrics metrics,
        int impactScore,
        Instant evaluatedAt
) {
}
