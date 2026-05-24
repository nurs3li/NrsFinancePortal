package com.nurseli.nrsfinanceportal.api.dto.portfolioai;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConfidenceLevel;

import java.time.Instant;
import java.util.List;

/**
 * Portfolio AI analiz response'u; skorlar, bulgular, varlık yorumları ve makro/haber etkisini taşır.
 */
public record PortfolioAiAnalysisResponse(
        String id,
        Instant createdAt,
        String title,
        PortfolioAiAnalysisType analysisType,
        int portfolioScore,
        int riskScore,
        PortfolioAiConfidenceLevel confidence,
        PortfolioAiConcentrationLevel concentrationRisk,
        String summary,
        List<String> findings,
        String scenarioComment,
        List<PortfolioAiAssetCommentDto> assetComments,
        String disclaimer,
        /** OPENAI | FALLBACK_RULE_BASED */
        String source,
        /** OpenAI model adı; fallback kayıtlarında null veya FALLBACK_RULE_BASED */
        String model,
        PortfolioAiPortfolioOverviewDto portfolioOverview,
        PortfolioAiDecisionPerspectiveDto decisionPerspective,
        PortfolioAiMacroAndNewsImpactDto macroAndNewsImpact,
        List<PortfolioAiAssetInsightDto> assetInsights,
        String finalNote
) {
}
