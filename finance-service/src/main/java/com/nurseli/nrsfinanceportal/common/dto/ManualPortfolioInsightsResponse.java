package com.nurseli.nrsfinanceportal.common.dto;

import java.util.List;

public record ManualPortfolioInsightsResponse(
        PortfolioInsightsSummaryDto summary,
        PortfolioHealthScoreDto healthScore,
        PortfolioConcentrationRiskDto concentrationRisk,
        List<PortfolioInsightItemDto> insights
) {}
