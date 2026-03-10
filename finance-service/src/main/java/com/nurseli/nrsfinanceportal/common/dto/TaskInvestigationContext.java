package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record TaskInvestigationContext(
        UserSummary user,
        AccountSummary account,
        SuspiciousEventSummary suspiciousEvent,
        List<WhaleEntry> whaleHistory,
        List<TxEntry> recentTransactions
) {

    public record UserSummary(
            Long id,
            String username,
            String email,
            String role,
            boolean whale,
            String whaleLevel
    ) {}

    public record AccountSummary(
            Long id,
            String type,
            String status,
            Instant frozenAt,
            String frozenReason
    ) {}

    public record SuspiciousEventSummary(
            Long id,
            String reason,
            BigDecimal amount,
            Long countInWindow,
            BigDecimal thresholdAmount,
            Integer thresholdCount,
            Instant occurredAt
    ) {}

    public record WhaleEntry(
            Long id,
            String whaleLevel,
            Integer impactScore,
            String reason,
            Instant triggeredAt
    ) {}

    public record TxEntry(
            Long id,
            String type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            LocalDateTime createdAt
    ) {}
}
