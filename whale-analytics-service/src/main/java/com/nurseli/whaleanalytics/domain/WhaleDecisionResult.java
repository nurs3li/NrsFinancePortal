package com.nurseli.whaleanalytics.domain;

import java.time.Instant;

public record WhaleDecisionResult(
        String userId,
        WhaleLevel level,
        WhaleMetrics metrics,
        TrendResult trend,
        PatternResult pattern,
        BehaviorClass behavior,
        RiskLevel risk,
        int impactScore,
        Instant evaluatedAt
) {}