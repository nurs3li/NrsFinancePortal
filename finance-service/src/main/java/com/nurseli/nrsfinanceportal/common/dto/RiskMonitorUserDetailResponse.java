package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record RiskMonitorUserDetailResponse(
        Long userId,
        String username,
        String email,
        PortfolioPerformanceDto portfolio,
        List<PortfolioSlice> portfolioSlices,
        WhaleLatest latestWhale,
        List<WhaleTimelineResponse> recentWhaleEvents,
        List<TransactionRiskView> recentTransactions
) {
    public record PortfolioSlice(
            String assetType,
            BigDecimal valueTry,
            BigDecimal ratioPct
    ) {}

    public record WhaleLatest(
            String whaleLevel,
            String behavior,
            String pattern,
            String risk,
            Integer impactScore,
            Instant triggeredAt
    ) {}

    public record TransactionRiskView(
            Long id,
            String type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            LocalDateTime createdAt,
            Integer riskScore,
            String riskReason
    ) {}
}
