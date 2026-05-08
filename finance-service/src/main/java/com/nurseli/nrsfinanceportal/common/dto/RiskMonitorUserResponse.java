package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RiskMonitorUserResponse(
        Long userId,
        String username,
        String email,
        BigDecimal portfolioTotalTry,
        String whaleLevel,
        String risk,
        String reason,
        Instant lastTriggeredAt,
        long eventCount
) {}
