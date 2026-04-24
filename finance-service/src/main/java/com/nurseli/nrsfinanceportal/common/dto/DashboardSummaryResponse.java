package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
            Map<AssetType, BigDecimal> distribution,
            BigDecimal totalCostTry,
            BigDecimal totalPnlTry,
            BigDecimal totalPnlPct,
            List<PortfolioCategoryBreakdown> categories
    ) {}

    /**
     * Birleşik portföy (TRADE + MANUAL) — varlık sınıfına göre toplamlar ve PnL.
     */
    public record PortfolioCategoryBreakdown(
            AssetType assetType,
            BigDecimal valueTry,
            BigDecimal costTry,
            BigDecimal pnlTry,
            BigDecimal pnlPct
    ) {}


    public record ActivitySummary(
            Instant lastTradeAt,
            int todayTradeCount
    ) {}
}
