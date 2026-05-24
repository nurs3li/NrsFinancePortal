package com.nurseli.nrsfinanceportal.api.dto;

import java.util.List;

/**
 * Manuel portföy insight response'u; özet metrikler, sağlık skoru, konsantrasyon riski ve önerileri taşır.
 */
public record ManualPortfolioInsightsResponse(
        PortfolioInsightsSummaryDto summary,
        PortfolioHealthScoreDto healthScore,
        PortfolioConcentrationRiskDto concentrationRisk,
        List<PortfolioInsightItemDto> insights
) {}
