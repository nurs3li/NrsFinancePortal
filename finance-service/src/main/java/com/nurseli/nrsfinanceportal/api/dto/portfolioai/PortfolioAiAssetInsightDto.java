package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import java.util.List;

/**
 * Portfolio AI varlık insight DTO'su; portföy etkisi, görüşler ve izlenmesi gereken noktaları taşır.
 */
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
