package com.nurseli.nrsfinanceportal.domain.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionReversedEvent(
        Long reversalTransactionId,
        Long originalTransactionId,
        Long accountId,
        Long adminUserId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime occurredAt
) {}
