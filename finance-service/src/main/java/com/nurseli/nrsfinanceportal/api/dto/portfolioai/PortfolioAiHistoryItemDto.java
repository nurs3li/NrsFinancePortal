package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;

import java.time.Instant;

/**
 * Portfolio AI geçmiş analiz satırı DTO'su; id, tarih, tip, skorlar ve özet metnini taşır.
 */
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
