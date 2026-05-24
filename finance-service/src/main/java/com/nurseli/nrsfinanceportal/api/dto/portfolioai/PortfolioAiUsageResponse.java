package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import java.time.Instant;

/**
 * Portfolio AI kullanım kotası response'u; günlük limit, kullanım ve son analiz bilgisini taşır.
 */
public record PortfolioAiUsageResponse(
        int dailyLimit,
        int usedToday,
        int remainingToday,
        Instant lastAnalysisAt,
        Integer lastPortfolioScore,
        Integer lastRiskScore
) {
}
