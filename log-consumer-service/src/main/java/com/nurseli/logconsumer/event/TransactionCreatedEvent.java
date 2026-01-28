package com.nurseli.logconsumer.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionCreatedEvent(
        Long transactionId,
        Long accountId,
        Long userId,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant occurredAt
) {}
