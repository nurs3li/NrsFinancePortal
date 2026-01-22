package com.nurseli.nrsfinanceportal.domain.event;

import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionCreatedEvent(
        Long transactionId,
        Long accountId,
        Long userId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime occurredAt
) {}
