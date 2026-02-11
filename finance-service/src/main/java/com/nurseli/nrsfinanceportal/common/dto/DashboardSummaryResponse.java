package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record DashboardSummaryResponse(

        WhaleSummary whale,
        CashSummary cash,
        PortfolioSummary portfolio,
        ActivitySummary activity,
        BigDecimal netWorthTry

) {

    public record WhaleSummary(
            String level,
            Integer impactScore,
            Instant triggeredAt
    ) {}
    public record CashSummary(
            BigDecimal amountTry
    ) {}
    public record PortfolioSummary(
            BigDecimal totalValueTry,
            Map<AssetType, BigDecimal> distribution
    ) {}


    public record ActivitySummary(
            Instant lastTradeAt,
            int todayTradeCount
    ) {}
}
