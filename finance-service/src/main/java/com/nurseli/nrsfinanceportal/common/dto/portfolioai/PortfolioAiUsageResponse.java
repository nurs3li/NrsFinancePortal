package com.nurseli.nrsfinanceportal.common.dto.portfolioai;

import java.time.Instant;

public record PortfolioAiUsageResponse(
        int dailyLimit,
        int usedToday,
        int remainingToday,
        Instant lastAnalysisAt,
        Integer lastPortfolioScore,
        Integer lastRiskScore
) {
}
