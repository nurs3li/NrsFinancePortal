package com.nurseli.metricsservice.event;

import java.math.BigDecimal;
import java.time.Instant;

public record SuspiciousActivityDetectedEvent(
        Long userId,
        Long transactionId,
        String reason,           // "HIGH_FREQUENCY" | "HIGH_AMOUNT"
        BigDecimal amount,
        Long countInWindow,
        BigDecimal thresholdAmount,
        Integer thresholdCount,
        Instant occurredAt
) {}