package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import java.util.List;

public record PortfolioAiAssetInsightDto(
        String symbol,
        String assetName,
        String assetClass,
        double weightPct,
        Double returnPct,
        String role,
        String impactOnPortfolio,
        String positiveView,
        String riskView,
        List<String> whatToWatch,
        String shortComment,
        String detailComment
) {
}
