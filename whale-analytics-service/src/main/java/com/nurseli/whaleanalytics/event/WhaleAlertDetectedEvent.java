package com.nurseli.whaleanalytics.event;

import com.nurseli.whaleanalytics.domain.WhaleLevel;

import java.math.BigDecimal;
import java.time.Instant;
public record WhaleAlertDetectedEvent(
        Long userId,
        WhaleLevel whaleLevel,
        int impactScore,
        BigDecimal dailyVolume,
        int hourlyTransactionCount,
        BigDecimal maxSingleTransaction,
        String trendDirection,      // STABLE/UPWARD/DOWNWARD/VOLATILE
        BigDecimal trendVelocity,
        BigDecimal trendVolatility,
        String pattern,             // ACCUMULATION / NONE / ...
        String behavior,            // CONSERVATIVE / AGGRESSIVE / ...
        String risk,                // LOW / MEDIUM / HIGH / CRITICAL
        Instant triggeredAt
) {}