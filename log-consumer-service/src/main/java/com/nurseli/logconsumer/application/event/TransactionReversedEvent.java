package com.nurseli.logconsumer.application.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code finance.transaction.reversed} Kafka topic'inden deserialize edilen işlem iptal event DTO'su.
 */
public record TransactionReversedEvent(
        Long reversalTransactionId,
        Long originalTransactionId,
        Long accountId,
        Long adminUserId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant occurredAt
) {}
