package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;

import java.time.Instant;

public record PortfolioAiHistoryItemDto(
        String id,
        Instant createdAt,
        PortfolioAiAnalysisType analysisType,
        String title,
        int portfolioScore,
        int riskScore,
        String summary
) {
}
