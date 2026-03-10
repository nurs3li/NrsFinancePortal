package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WhaleTimelineResponse(
        Long id,
        Long userId,
        String whaleLevel,
        Integer impactScore,
        String reason,
        BigDecimal dailyVolume,
        Integer hourlyTransactionCount,
        BigDecimal maxSingleTransaction,
        String pattern,
        String behavior,
        String risk,
        Instant triggeredAt,
        Instant createdAt
) {}