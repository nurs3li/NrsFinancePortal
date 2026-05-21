package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

public record PortfolioAiPortfolioOverviewDto(
        String summary,
        String currentSituation,
        String mainPositive,
        String mainRisk
) {
}
