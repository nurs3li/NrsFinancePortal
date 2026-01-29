package com.nurseli.whaleanalytics.event;

import com.nurseli.whaleanalytics.domain.WhaleLevel;

import java.math.BigDecimal;
import java.time.Instant;

public record WhaleAlertDetectedEvent(
        Long userId,
        WhaleLevel level,
        BigDecimal dailyVolume,
        int hourlyCount,
        BigDecimal maxSingleTx,
        Instant triggeredAt
) {}
