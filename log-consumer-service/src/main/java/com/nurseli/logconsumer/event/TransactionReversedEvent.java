package com.nurseli.logconsumer.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionReversedEvent(
        Long reversalTransactionId,
        Long originalTransactionId,
        Long accountId,
        BigDecimal amount,
        Instant occurredAt
) {}
