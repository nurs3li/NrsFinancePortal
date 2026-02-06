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
        Instant triggeredAt
) {
}
