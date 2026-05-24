package com.nurseli.logconsumer.application.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code finance.transaction.created} Kafka topic'inden deserialize edilen işlem oluşturma event DTO'su.
 */
public record TransactionCreatedEvent(
        Long transactionId,
        Long accountId,
        Long userId,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant occurredAt
) {}
