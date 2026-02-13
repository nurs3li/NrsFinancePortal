package com.nurseli.metricsservice.event;

import java.math.BigDecimal;
import java.time.Instant;

public record WhaleAlertDetectedEvent(
        Long userId,
        String whaleLevel,     // "NONE" | "L1_LARGE_TRADER" | "L2_WHALE" | "L3_MEGA_WHALE"
        Integer impactScore,
        BigDecimal dailyVolume,
        Integer hourlyTransactionCount,
        BigDecimal maxSingleTransaction,
        String trendDirection,
        BigDecimal trendVelocity,
        BigDecimal trendVolatility,
        String pattern,
        String behavior,
        String risk,
        Instant triggeredAt
) {}