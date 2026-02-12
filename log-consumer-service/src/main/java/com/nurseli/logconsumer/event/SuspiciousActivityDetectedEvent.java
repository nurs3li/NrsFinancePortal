package com.nurseli.logconsumer.event;

import java.math.BigDecimal;
import java.time.Instant;

public record SuspiciousActivityDetectedEvent(
        Long userId,
        Long transactionId,
        String reason,
        BigDecimal amount,
        Long countInWindow,
        BigDecimal thresholdAmount,
        Integer thresholdCount,
        Instant occurredAt
) {}