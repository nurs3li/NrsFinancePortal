package com.nurseli.nrsfinanceportal.integration.kafka.event;

import com.nurseli.nrsfinanceportal.domain.whale.WhaleLevel;

import java.math.BigDecimal;
import java.time.Instant;

public record WhaleAlertTriggeredEvent(
        Long userId,
        WhaleLevel whaleLevel,
        Integer impactScore,
        BigDecimal dailyVolume,
        int hourlyTransactionCount,
        BigDecimal maxSingleTransaction,
        Instant triggeredAt
) {}
