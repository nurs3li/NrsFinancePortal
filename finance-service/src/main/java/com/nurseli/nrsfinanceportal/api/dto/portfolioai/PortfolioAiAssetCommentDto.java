package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import java.util.List;

/**
 * Portfolio AI varlık yorumu DTO'su; ağırlık, getiri, risk skoru ve olumlu/risk faktörlerini taşır.
 */
public record PortfolioAiAssetCommentDto(
        String symbol,
        String assetName,
        String assetClass,
        double weightPct,
        Double returnPct,
        int assetScore,
        int riskScore,
        String riskLevel,
        String role,
        List<String> positiveFactors,
        List<String> riskFactors,
        String shortComment,
        String detailComment
) {
}
