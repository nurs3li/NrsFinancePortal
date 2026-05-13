package com.nurseli.whaleanalytics.domain.investor;

public record PortfolioImpactBreakdown(
        int portfolioImpactScore,
        int assetConcentrationScore,
        int profitScore,
        int realProfitScore,
        int riskExposureScore
) {}
