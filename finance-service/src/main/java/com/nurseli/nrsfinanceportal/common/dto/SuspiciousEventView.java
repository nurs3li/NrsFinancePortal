package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.suspicious.SuspiciousEvent;
import com.nurseli.nrsfinanceportal.domain.user.User;

import java.math.BigDecimal;
import java.time.Instant;

public record SuspiciousEventView(
        Long userId,
        String username,
        String email,
        Long transactionId,
        String reason,
        BigDecimal amount,
        Long countInWindow,
        BigDecimal thresholdAmount,
        Integer thresholdCount,
        Instant occurredAt
) {

    public static SuspiciousEventView of(SuspiciousEvent e, User user) {
        return new SuspiciousEventView(
                e.getUserId(),
                user != null ? user.getUsername() : null,
                user != null ? user.getEmail() : null,
                e.getTransactionId(),
                e.getReason(),
                e.getAmount(),
                e.getCountInWindow(),
                e.getThresholdAmount(),
                e.getThresholdCount(),
                e.getOccurredAt()
        );
    }
}