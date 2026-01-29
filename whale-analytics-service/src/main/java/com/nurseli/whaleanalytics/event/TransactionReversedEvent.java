package com.nurseli.whaleanalytics.event;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionReversedEvent(
        Long reversalTransactionId,
        Long originalTransactionId,
        Long accountId,
        Long adminUserId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant occurredAt
) {}
