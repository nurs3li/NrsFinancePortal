package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import java.util.List;

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
