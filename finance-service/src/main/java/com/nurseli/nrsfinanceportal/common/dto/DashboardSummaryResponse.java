package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(

        PortfolioSummary portfolio,
        BigDecimal totalPortfolioValueTry

) {

    public record PortfolioSummary(
            BigDecimal totalValueTry,
            Map<AssetType, BigDecimal> distribution,
            BigDecimal totalCostTry,
            BigDecimal totalPnlTry,
            BigDecimal totalPnlPct,
            List<PortfolioCategoryBreakdown> categories
    ) {}

    public record PortfolioCategoryBreakdown(
            AssetType assetType,
            BigDecimal valueTry,
            BigDecimal costTry,
            BigDecimal pnlTry,
            BigDecimal pnlPct
    ) {}
}
