package com.nurseli.nrsfinanceportal.integration.kafka.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Şüpheli davranış tespit edildiğinde yayımlanır (in-process ve Kafka).
 */
public record SuspiciousActivityDetectedEvent(
        Long userId,
        Long transactionId,
        String reason,           // "HIGH_FREQUENCY" | "HIGH_AMOUNT"
        BigDecimal amount,
        Long countInWindow,      // HIGH_FREQUENCY için dakikadaki işlem sayısı; HIGH_AMOUNT için null
        BigDecimal thresholdAmount,  // HIGH_AMOUNT için eşik; HIGH_FREQUENCY için null
        Integer thresholdCount,  // HIGH_FREQUENCY için eşik; HIGH_AMOUNT için null
        Instant occurredAt
) {}