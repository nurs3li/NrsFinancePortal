package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

/**
 * Portfolio AI portföy genel bakış DTO'su; mevcut durum, ana olumlu ve risk unsurlarını taşır.
 */
public record PortfolioAiPortfolioOverviewDto(
        String summary,
        String currentSituation,
        String mainPositive,
        String mainRisk
) {
}
